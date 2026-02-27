/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.sensorhub.api.security.IPermission;
import org.sensorhub.api.security.IPermissionPath;
import org.sensorhub.impl.security.BasicSecurityRealmConfig;
import org.sensorhub.impl.security.BasicSecurityRealmConfig.PermissionsConfig;
import org.sensorhub.impl.security.BasicSecurityRealmConfig.RoleConfig;
import org.sensorhub.impl.security.PermissionSetting;
import org.sensorhub.ui.ValueEntryPopup.ValueCallback;
import org.sensorhub.ui.data.ContainerProperty;
import org.sensorhub.ui.data.MyBeanItem;
import com.vaadin.v7.data.Item;
import com.vaadin.v7.data.fieldgroup.FieldGroup;
import com.vaadin.v7.data.util.converter.Converter;
import com.vaadin.event.Action;
import com.vaadin.event.Action.Handler;
import com.vaadin.server.FontAwesome;
import com.vaadin.ui.Button;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.v7.ui.Table;
import com.vaadin.v7.ui.TreeTable;
import com.vaadin.v7.ui.Field;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.v7.ui.Table.CellStyleGenerator;
import com.vaadin.v7.ui.Table.ColumnHeaderMode;
import com.vaadin.ui.Label;
import com.vaadin.shared.ui.ContentMode;
import com.vaadin.ui.TextField;
import org.sensorhub.impl.security.TOTPUtils;
import com.vaadin.server.StreamResource;
import com.vaadin.ui.Image;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;


@SuppressWarnings("serial")
public class BasicSecurityConfigForm extends GenericConfigForm
{
    private static final Action ALLOW_ACTION = new Action("Allow", FontAwesome.CHECK);
    private static final Action DENY_ACTION = new Action("Deny", FontAwesome.BAN);
    private static final Action CLEAR_ACTION = new Action("Clear", FontAwesome.TIMES);
    
    protected static final String PROP_USER_ROLES = "users.roles";
    protected static final String PROP_ALLOW_LIST = ".allow";
    protected static final String PROP_DENY_LIST = ".deny";
    protected static final String PROP_PERMISSION = "perm";
    protected static final String PROP_STATE = "state";
    
    private enum PermState {ALLOW, DENY, INHERIT_ALLOW, INHERIT_DENY, UNSET}
    private transient PermissionsConfig permConfig;
    private transient TreeTable permissionTable;
    
    
    @Override
    public void build(String title, String popupText, MyBeanItem<Object> beanItem, boolean includeSubForms)
    {
        if (beanItem.getBean() instanceof PermissionsConfig)
            this.permConfig = (PermissionsConfig)beanItem.getBean();
        
        super.build(title, popupText, beanItem, includeSubForms);

        if (beanItem.getBean() instanceof BasicSecurityRealmConfig.UserConfig)
            buildTwoFactorSection((BasicSecurityRealmConfig.UserConfig)beanItem.getBean());
    }

    @Override
    protected boolean isFieldVisible(String propId)
    {
        if (propId.endsWith("twoFactorSecret") || propId.endsWith("isTwoFactorEnabled"))
            return false;

        return super.isFieldVisible(propId);
    }

    private void buildTwoFactorSection(final BasicSecurityRealmConfig.UserConfig userConfig)
    {
        VerticalLayout layout = new VerticalLayout();
        layout.setCaption("Two-Factor Authentication");
        layout.setMargin(true);
        layout.setSpacing(true);

        final Label statusLabel = new Label();
        statusLabel.setContentMode(ContentMode.HTML);
        update2FAStatusLabel(statusLabel, userConfig.isTwoFactorEnabled);
        layout.addComponent(statusLabel);

        final Button setupButton = new Button(userConfig.isTwoFactorEnabled ? "Reset 2FA" : "Enable 2FA");
        setupButton.addClickListener(new ClickListener() {
            @Override
            public void buttonClick(ClickEvent event)
            {
                show2FASetupPopup(userConfig, statusLabel, setupButton);
            }
        });
        layout.addComponent(setupButton);

        this.addComponent(layout);
    }

    private void update2FAStatusLabel(Label label, boolean enabled)
    {
        if (enabled)
            label.setValue("Status: <span style='color:green;font-weight:bold'>ENABLED</span>");
        else
            label.setValue("Status: <span style='color:red;font-weight:bold'>DISABLED</span>");
    }

    private void show2FASetupPopup(final BasicSecurityRealmConfig.UserConfig userConfig, final Label statusLabel, final Button setupButton)
    {
        // retrieve user ID from field if possible as it may have changed
        String userID = userConfig.userID;
        if (this.fieldGroup != null)
        {
            Field<?> field = this.fieldGroup.getField("userID");
            if (field != null && field.getValue() != null)
                userID = field.getValue().toString();
        }

        if (userID == null || userID.trim().isEmpty())
        {
            Notification.show("Please enter a User ID first", Notification.Type.WARNING_MESSAGE);
            return;
        }

        final Window popup = new Window("Setup Two-Factor Authentication");
        popup.setModal(true);
        popup.setWidth("400px");
        popup.center();

        VerticalLayout content = new VerticalLayout();
        content.setMargin(true);
        content.setSpacing(true);

        final String secret = TOTPUtils.generateSecret();
        final String qrUrl = TOTPUtils.getQRUrl(userID, secret);

        content.addComponent(new Label("1. Scan this QR Code with your authenticator app:"));

        if (qrUrl != null)
        {
            try
            {
                StreamResource resource = new StreamResource(() -> {
                    try {
                        QRCodeWriter qrCodeWriter = new QRCodeWriter();
                        BitMatrix bitMatrix = qrCodeWriter.encode(qrUrl, BarcodeFormat.QR_CODE, 200, 200);
                        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
                        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
                        return new ByteArrayInputStream(pngOutputStream.toByteArray());
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }, "qr_" + System.currentTimeMillis() + ".png");

                Image qrImage = new Image(null, resource);
                content.addComponent(qrImage);
            }
            catch (Exception e)
            {
                content.addComponent(new Label("Error generating QR Code"));
            }
        }

        content.addComponent(new Label("Or enter this Secret Key manually:"));

        // format secret for display
        String secretDisplay = secret.replaceAll("(.{4})", "$1 ").trim();
        content.addComponent(new Label("<span style='font-family:monospace;font-size:1.2em;font-weight:bold'>" + secretDisplay + "</span>", ContentMode.HTML));

        content.addComponent(new Label("2. Enter the 6-digit code generated by the app:"));

        final TextField codeField = new TextField();
        content.addComponent(codeField);

        Button verifyButton = new Button("Verify and Enable");
        verifyButton.addClickListener(new ClickListener() {
            @Override
            public void buttonClick(ClickEvent event)
            {
                if (TOTPUtils.validateCode(secret, codeField.getValue()))
                {
                    userConfig.twoFactorSecret = secret;
                    userConfig.isTwoFactorEnabled = true;
                    update2FAStatusLabel(statusLabel, true);
                    setupButton.setCaption("Reset 2FA");
                    popup.close();
                    com.vaadin.ui.Notification.show("2FA Enabled Successfully");
                }
                else
                {
                    com.vaadin.ui.Notification.show("Invalid Code", com.vaadin.ui.Notification.Type.ERROR_MESSAGE);
                }
            }
        });
        content.addComponent(verifyButton);

        popup.setContent(content);
        getUI().addWindow(popup);
    }
    
    
    @Override
    public List<Object> getPossibleValues(String propId)
    {
        if (propId.equals(PROP_USER_ROLES))
        {
            GenericConfigForm parentForm = (GenericConfigForm)getParentForm();
            MyBeanItem<BasicSecurityRealmConfig> beanItem = (MyBeanItem<BasicSecurityRealmConfig>)parentForm.fieldGroup.getItemDataSource();
            List<Object> allRoles = new ArrayList<>();
            for (RoleConfig role: beanItem.getBean().roles)
                allRoles.add(role.getId());
            return allRoles;
        }
        
        return super.getPossibleValues(propId);
    }
    
    
    @Override
    protected void buildListComponent(final String propId, final ContainerProperty prop, final FieldGroup fieldGroup)
    {
        if (propId.endsWith(PROP_ALLOW_LIST))
        {
            HorizontalLayout layout = new HorizontalLayout();
            layout.setWidth(100.0f, Unit.PERCENTAGE);
            layout.setSpacing(true);
            layout.setCaption("Permissions");
            layout.setDescription("Allowed and denied permissions for users with this role");
            
            // permission table
            buildTable(layout);
            
            // add/remove buttons
            buildButtons(layout);
            
            subForms.add(layout);
        }
        
        // skip deny list since we handle it with same component as allow list
        else if (propId.endsWith(PROP_DENY_LIST))
            return;
        
        else
            super.buildListComponent(propId, prop, fieldGroup);
    }
    
    
    private void buildTable(HorizontalLayout layout)
    {
        // permission table
        final TreeTable table = new TreeTable();
        table.setSizeFull();
        table.setHeight(500f, Unit.PIXELS);
        table.setSelectable(true);
        table.setNullSelectionAllowed(false);
        table.setImmediate(true);
        table.setColumnReorderingAllowed(false);
        table.addContainerProperty(PROP_PERMISSION, IPermission.class, null);
        table.addContainerProperty(PROP_STATE, PermState.class, PermState.UNSET);
        table.setColumnHeaderMode(ColumnHeaderMode.EXPLICIT_DEFAULTS_ID);
        table.setColumnHeader(PROP_PERMISSION, "Permission Name");
        table.setColumnHeader(PROP_STATE, "Allow/Deny");
        
        // cell converter for name
        table.setConverter(PROP_PERMISSION, new Converter<String, IPermission>() {
            @Override
            public IPermission convertToModel(String value, Class<? extends IPermission> targetType, Locale locale)
            {
                return null; // not needed since it's not editable
            }

            @Override
            public String convertToPresentation(IPermission value, Class<? extends String> targetType, Locale locale)
            {
                if (value == null)
                    return null;
                
                StringBuilder name = new StringBuilder(value.toString());
                name.setCharAt(0, Character.toUpperCase(name.charAt(0)));
                return name.toString();
            }

            @Override
            public Class<IPermission> getModelType()
            {
                return IPermission.class;
            }

            @Override
            public Class<String> getPresentationType()
            {
                return String.class;
            }
        });
        
        // cell converter for state
        table.setConverter(PROP_STATE, new Converter<String, PermState>() {
            @Override
            public PermState convertToModel(String value, Class<? extends PermState> targetType, Locale locale)
            {
                return PermState.valueOf(value);
            }

            @Override
            public String convertToPresentation(PermState value, Class<? extends String> targetType, Locale locale)
            {
                switch (value)
                {
                    case ALLOW:
                    case INHERIT_ALLOW:
                        return "Allow";
                        
                    case DENY:
                    case INHERIT_DENY:
                        return "Deny";
                        
                    case UNSET:
                    default:
                        return "Deny (Default)";
                }
            }

            @Override
            public Class<PermState> getModelType()
            {
                return PermState.class;
            }

            @Override
            public Class<String> getPresentationType()
            {
                return String.class;
            }
        });
        
        // cell style depending on state
        table.setCellStyleGenerator(new CellStyleGenerator() {
            @Override
            public String getStyle(Table source, Object itemId, Object propertyId)
            {
                if (propertyId != null && propertyId.equals(PROP_STATE))
                {
                    PermState state = (PermState)table.getItem(itemId).getItemProperty(PROP_STATE).getValue();
                    
                    switch (state)
                    {
                        case ALLOW:
                            return "perm-allow";
                            
                        case INHERIT_ALLOW:
                            return "perm-allow-gray";
                            
                        case DENY:
                            return "perm-deny";
                            
                        case INHERIT_DENY:
                            return "perm-deny-gray";
                            
                        case UNSET:
                            return "perm-deny-gray";
                    }
                }
                
                return null;
            }
        });
        
        // context menu
        table.addActionHandler(new Handler() {
            @Override
            public Action[] getActions(Object target, Object sender)
            {
                List<Action> actions = new ArrayList<>(10);
                                
                if (target != null)
                {                    
                    PermState state = (PermState)table.getItem(target).getItemProperty(PROP_STATE).getValue();
                    
                    if (state == PermState.ALLOW)
                    {
                        actions.add(CLEAR_ACTION);
                        actions.add(DENY_ACTION);
                    }
                    
                    else if (state == PermState.DENY)
                    {
                        actions.add(CLEAR_ACTION);
                        actions.add(ALLOW_ACTION);
                    }
                    
                    else
                    {
                        actions.add(ALLOW_ACTION);
                        actions.add(DENY_ACTION);
                    }
                }
                
                return actions.toArray(new Action[0]);
            }
            
            @Override
            public void handleAction(Action action, Object sender, Object target)
            {
                final Object selectedId = table.getValue();
                                    
                if (selectedId != null)
                {
                    String permPath = getPermissionPath(selectedId);
                    
                    if (action == ALLOW_ACTION)
                    {                            
                        permConfig.allow.add(permPath);
                        permConfig.deny.remove(permPath);
                    }
                    else if (action == DENY_ACTION)
                    {                            
                        permConfig.deny.add(permPath);
                        permConfig.allow.remove(permPath);
                    }
                    else if (action == CLEAR_ACTION)
                    {
                        permConfig.allow.remove(permPath);
                        permConfig.deny.remove(permPath);
                    }
                    
                    permConfig.refreshPermissionLists(getParentHub().getModuleRegistry());
                    refreshPermissions(table);
                }
            }
        });
        
        // detect all modules for which permissions are set
        // and add all root permissions to tree
        HashSet<String> moduleIdStrings = new HashSet<>();
        addTopLevelPermissions(moduleIdStrings, permConfig.allow);
        addTopLevelPermissions(moduleIdStrings, permConfig.deny);
        for (String moduleIdString: moduleIdStrings)
        {
            IPermission perm = getParentHub().getSecurityManager().getModulePermissions(moduleIdString);
            if (perm != null)
                addPermToTree(table, perm, null);
        }
        
        this.permissionTable = table;
        layout.addComponent(table);
    }
    
    
    private void buildButtons(HorizontalLayout layout)
    {
        VerticalLayout buttons = new VerticalLayout();
        
        // add button
        Button addBtn = new Button(ADD_ICON);
        addBtn.addStyleName(STYLE_QUIET);
        addBtn.addStyleName(STYLE_SMALL);
        buttons.addComponent(addBtn);
        addBtn.addClickListener(new ClickListener() {
            @Override
            public void buttonClick(ClickEvent event)
            {
                // get list of top level permissions registered with security manager
                Collection<IPermission> valueList = getParentHub().getSecurityManager().getAllModulePermissions();
                
                // create callback to add new value
                ValueCallback callback = new ValueCallback() {
                    @Override
                    public void newValue(Object value)
                    {
                        addPermToTree(permissionTable, (IPermission)value, null);
                    }
                };
        
                Window popup = new ValueEntryPopup(600, callback, valueList);
                popup.setModal(true);
                getUI().addWindow(popup);
            }
        });
        
        // remove button
        Button delBtn = new Button(DEL_ICON);
        delBtn.addStyleName(STYLE_QUIET);
        delBtn.addStyleName(STYLE_SMALL);
        buttons.addComponent(delBtn);
        delBtn.addClickListener(new ClickListener() {
            @Override
            public void buttonClick(ClickEvent event)
            {
                Object itemId = permissionTable.getValue();
                if (itemId != null)
                    removeItemsRecursively(itemId);
            }
        });
        
        layout.addComponent(buttons);
    }
    
    
    private void removeItemsRecursively(Object itemId)
    {
        Collection<?> children = permissionTable.getChildren(itemId);
        if (children != null)
        {
            // need to wrap collection to avoid concurrency exception
            for (Object childId: new ArrayList<Object>(children))
                removeItemsRecursively(childId);
        }
        
        // remove permission from config
        String permPath = getPermissionPath(itemId);
        permConfig.allow.remove(permPath);
        permConfig.deny.remove(permPath);
        
        // also remove it from table
        permissionTable.removeItem(itemId);
    }
    
    
    private String getPermissionPath(Object itemId)
    {
        Item item = permissionTable.getItem(itemId);
        IPermission perm = (IPermission)item.getItemProperty(PROP_PERMISSION).getValue();
        String permPath = perm.getFullName();
        if (perm.hasChildren())
            permPath += "/*";
        return permPath;
    }
    
    
    private void addTopLevelPermissions(HashSet<String> moduleIdStrings, List<String> permStringList)
    {
        for (String permString: permStringList)
        {
            String moduleIdString = permString;
            int endModuleId = permString.indexOf('/');
            if (endModuleId > 0)
                moduleIdString = permString.substring(0, endModuleId);
            moduleIdStrings.add(moduleIdString);
        }
    }
    
    
    private void refreshPermissions(TreeTable table)
    {
        for (Object itemId: table.getContainerDataSource().getItemIds())
        {
            Item item = table.getItem(itemId);
            IPermission perm = (IPermission)item.getItemProperty(PROP_PERMISSION).getValue();
            item.getItemProperty(PROP_STATE).setValue(getState(perm));
        }
    }
    
    
    private void addPermToTree(TreeTable table, IPermission perm, Object parentId)
    {
        Object newItemId = table.addItem();
        Item newItem = table.getItem(newItemId);
        newItem.getItemProperty(PROP_PERMISSION).setValue(perm);        
        newItem.getItemProperty(PROP_STATE).setValue(getState(perm));
        
        if (parentId != null)
            table.setParent(newItemId, parentId);
        
        if (perm.getChildren().isEmpty())
        {
            table.setChildrenAllowed(newItemId, false);
        }
        else
        {
            for (IPermission childPerm: perm.getChildren().values())
                addPermToTree(table, childPerm, newItemId);
        }        
    }
    
    
    private PermState getState(IPermission perm)
    {
        PermissionSetting permSetting = new PermissionSetting(perm);
        int permPathLength = permSetting.size();
        PermState permState = PermState.UNSET;
        
        // check allow list        
        for (IPermissionPath fromConfig: permConfig.getAllowList())
        {
            int configPermPathLength = fromConfig.size();
            
            if (fromConfig.implies(permSetting))
            {
                if (permPathLength == configPermPathLength)
                {
                    permState = PermState.ALLOW;
                    break;
                }
                else
                    permState = PermState.INHERIT_ALLOW;
            }
        }
        
        // check deny list
        for (IPermissionPath fromConfig: permConfig.getDenyList())
        {
            int configPermPathLength = fromConfig.size();
            
            if (fromConfig.implies(permSetting) && permPathLength >= configPermPathLength)
            {
                if (permPathLength == configPermPathLength)
                {
                    permState = PermState.DENY;
                    break;
                }
                else
                    permState = PermState.INHERIT_DENY;
            }
        }
        
        return permState;
    }
}
