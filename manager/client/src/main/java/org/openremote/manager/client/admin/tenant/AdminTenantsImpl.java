/*
 * Copyright 2016, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.manager.client.admin.tenant;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.*;
import org.openremote.manager.client.i18n.ManagerConstants;
import org.openremote.manager.client.style.FormTableStyle;
import org.openremote.manager.client.widget.PushButton;
import org.openremote.manager.shared.security.Tenant;

import javax.inject.Inject;
import java.util.Arrays;

public class AdminTenantsImpl extends Composite implements AdminTenants {

    interface UI extends UiBinder<HTMLPanel, AdminTenantsImpl> {
    }

    private UI ui = GWT.create(UI.class);

    Presenter presenter;

    @UiField
    AdminTenantsTable.Style tenantsTableStyle;

    @UiField
    PushButton createButton;

    @UiField
    SimplePanel cellTableContainer;

    final AdminTenantsTable table;

    @Inject
    public AdminTenantsImpl(ManagerConstants managerConstants,
                            FormTableStyle formTableStyle) {
        initWidget(ui.createAndBindUi(this));

        table = new AdminTenantsTable(managerConstants, tenantsTableStyle, formTableStyle);
        table.getSelectionModel().addSelectionChangeHandler(event -> {
                Tenant selected;
                if ((selected = table.getSelectedObject()) != null
                    && presenter != null) {
                    presenter.onTenantSelected(selected);
                }
            }
        );
        cellTableContainer.add(table);
    }

    @Override
    public void setPresenter(Presenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void setTenants(Tenant[] tenants) {
        table.setRowData(Arrays.asList(tenants));
    }

    @UiHandler("createButton")
    void createClicked(ClickEvent e) {
        presenter.createTenant();
    }
}
