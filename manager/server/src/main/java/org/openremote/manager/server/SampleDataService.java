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
package org.openremote.manager.server;

import elemental.json.Json;
import org.apache.log4j.Logger;
import org.keycloak.admin.client.resource.*;
import org.keycloak.common.enums.SslRequired;
import org.keycloak.representations.idm.*;
import org.openremote.container.Container;
import org.openremote.container.ContainerService;
import org.openremote.container.security.AuthForm;
import org.openremote.container.security.IdentityService;
import org.openremote.manager.server.assets.AssetsService;
import org.openremote.manager.server.assets.ContextBrokerResource;
import org.openremote.manager.server.security.ManagerIdentityService;
import org.openremote.manager.shared.ngsi.Attribute;
import org.openremote.manager.shared.ngsi.Entity;
import rx.Observable;

import javax.ws.rs.core.UriBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.openremote.manager.shared.Constants.MANAGER_CLIENT_ID;
import static org.openremote.manager.shared.Constants.MASTER_REALM;
import static rx.Observable.fromCallable;

public class SampleDataService implements ContainerService {

    private static final Logger LOG = Logger.getLogger(SampleDataService.class.getName());

    public static final String IMPORT_SAMPLE_DATA = "IMPORT_SAMPLE_DATA";
    public static final boolean IMPORT_SAMPLE_DATA_DEFAULT = false;

    public static final String ADMIN_CLI_CLIENT_ID = "admin-cli";
    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "admin";

    protected IdentityService identityService;
    protected AssetsService assetsService;
    /* TODO
    protected PersistenceService persistenceService
    */

    @Override
    public void init(Container container) throws Exception {
        identityService = container.getService(ManagerIdentityService.class);
        assetsService = container.getService(AssetsService.class);
    }

    @Override
    public void configure(Container container) throws Exception {

    }

    @Override
    public void start(Container container) {
        if (!container.isDevMode() && !container.getConfigBoolean(IMPORT_SAMPLE_DATA, IMPORT_SAMPLE_DATA_DEFAULT)) {
            return;
        }

        LOG.info("--- CREATING SAMPLE DATA ---");

        // Use a non-proxy client to get the access token
        String accessToken = identityService.getKeycloak().getAccessToken(
            MASTER_REALM, new AuthForm(ADMIN_CLI_CLIENT_ID, ADMIN_USERNAME, ADMIN_PASSWORD)
        ).getToken();

        configureMasterRealm(identityService, accessToken);
        registerClientApplications(identityService, accessToken);
        addRolesAndTestUsers(identityService, accessToken);

        createSampleRooms(assetsService);

        /* TODO
        persistenceService.createSchema();
        EntityManager em = persistenceService.getEntityManagerFactory().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        em.persist(someSampleData);
        tx.commit();
        */
    }

    @Override
    public void stop(Container container) {
    }

    protected void createSampleRooms(AssetsService assetsService) {
        Entity room1 = new Entity(Json.createObject());
        room1.setId("Room1");
        room1.setType("Room");
        room1.addAttribute(
            new Attribute("temperature", Json.createObject())
                .setType("float")
                .setValue(Json.create(21.3))
        ).addAttribute(
            new Attribute("label", Json.createObject())
                .setType("string")
                .setValue(Json.create("Office 123"))
        );

        Entity room2 = new Entity(Json.createObject());
        room2.setId("Room2");
        room2.setType("Room");
        room2.addAttribute(
            new Attribute("temperature", Json.createObject())
                .setType("float")
                .setValue(Json.create(22.1))
        ).addAttribute(
            new Attribute("label", Json.createObject())
                .setType("string")
                .setValue(Json.create("Office 456"))
        );

        ContextBrokerResource ngsiService = assetsService.getContextBroker();

        fromCallable(() -> ngsiService.getEntities(null))
            .map(Entity::from)
            .flatMap(Observable::from)
            .flatMap(entity -> fromCallable(() -> ngsiService.deleteEntity(entity.getId())))
            .toList().toBlocking().single();

        ngsiService.postEntity(room1);
        ngsiService.postEntity(room2);
    }

    protected void configureMasterRealm(IdentityService identityService, String accessToken) {
        RealmResource realmResource = identityService.getRealms(accessToken, false).realm(MASTER_REALM);
        RealmRepresentation masterRealm = realmResource.toRepresentation();

        masterRealm.setDisplayName("OpenRemote");
        masterRealm.setDisplayNameHtml("<div class=\"kc-logo-text\"><span>OpenRemote</span></div>");

        masterRealm.setLoginTheme("openremote");
        masterRealm.setAccountTheme("openremote");

        // TODO: Make SSL setup configurable
        masterRealm.setSslRequired(SslRequired.NONE.toString());

        // TODO: This should only be set in dev mode, 60 seconds is enough in production?
        masterRealm.setAccessTokenLifespan(900); // 15 minutes

        realmResource.update(masterRealm);
    }

    protected void registerClientApplications(IdentityService identityService, String accessToken) {
        ClientsResource clientsResource = identityService.getRealms(accessToken, false).realm(MASTER_REALM).clients();

        // Find out if there is a client already present for this application, if so, delete it
        fromCallable(clientsResource::findAll)
            .flatMap(Observable::from)
            .filter(clientRepresentation -> clientRepresentation.getClientId().equals(MANAGER_CLIENT_ID))
            .map(ClientRepresentation::getId)
            .subscribe(clientObjectId -> {
                clientsResource.get(clientObjectId).remove();
            });

        // Register a new client for this application
        ClientRepresentation managerClient = new ClientRepresentation();

        managerClient.setRegistrationAccessToken(accessToken);

        managerClient.setClientId(MANAGER_CLIENT_ID);

        managerClient.setName("OpenRemote Manager");
        managerClient.setPublicClient(true);

        // TODO this should only be enabled in dev mode, we need it for integration tests
        managerClient.setDirectAccessGrantsEnabled(true);

        String callbackUrl = UriBuilder.fromUri("/").path(MASTER_REALM).path("*").build().toString();

        List<String> redirectUrls = new ArrayList<>();
        redirectUrls.add(callbackUrl);
        managerClient.setRedirectUris(redirectUrls);

        String baseUrl = UriBuilder.fromUri("/").path(MASTER_REALM).build().toString();
        managerClient.setBaseUrl(baseUrl);

        String clientResourceLocation =
            clientsResource.create(managerClient).getLocation().toString();

        LOG.info("Registered client application '" + MANAGER_CLIENT_ID + "' with identity provider: " + clientResourceLocation);
    }

    protected void addRolesAndTestUsers(IdentityService identityService, String accessToken) {
        ClientsResource clientsResource = identityService.getRealms(accessToken, false).realm(MASTER_REALM).clients();
        UsersResource usersResource = identityService.getRealms(accessToken, false).realm(MASTER_REALM).users();

        String clientObjectId = fromCallable(clientsResource::findAll)
            .flatMap(Observable::from)
            .filter(clientRepresentation -> clientRepresentation.getClientId().equals(MANAGER_CLIENT_ID))
            .map(ClientRepresentation::getId)
            .toBlocking().singleOrDefault(null);

        // Register some roles
        ClientResource clientResource = clientsResource.get(clientObjectId);
        RolesResource rolesResource = clientResource.roles();

        rolesResource.create(new RoleRepresentation("read", "Read all data", false));
        RoleRepresentation readRole = rolesResource.get("read").toRepresentation();
        LOG.info("Added role '" + readRole.getName() + "'");

        rolesResource.create(new RoleRepresentation("read:map", "View map", false));
        RoleRepresentation readMapRole = rolesResource.get("read:map").toRepresentation();
        LOG.info("Added role '" + readMapRole.getName() + "'");

        rolesResource.create(new RoleRepresentation("read:assets", "Read context broker assets", false));
        RoleRepresentation readAssetsRole = rolesResource.get("read:assets").toRepresentation();
        LOG.info("Added role '" + readAssetsRole.getName() + "'");

        rolesResource.get("read").addComposites(Arrays.asList(readMapRole, readAssetsRole));

        rolesResource.create(new RoleRepresentation("write", "Write all data", false));
        RoleRepresentation writeRole = rolesResource.get("write").toRepresentation();
        LOG.info("Added role '" + writeRole.getName() + "'");

        rolesResource.create(new RoleRepresentation("write:assets", "Write context broker assets", false));
        RoleRepresentation writeAssetsRole = rolesResource.get("write:assets").toRepresentation();
        LOG.info("Added role '" + writeAssetsRole.getName() + "'");

        rolesResource.get("write").addComposites(Arrays.asList(writeAssetsRole));


        // Give admin all roles (we can only check realm _or_ application roles in @RolesAllowed)!
        fromCallable(() -> usersResource.search("admin", null, null, null, null, null))
            .flatMap(Observable::from)
            .map(userRepresentation -> usersResource.get(userRepresentation.getId()))
            .subscribe(adminUser -> {
                    adminUser.roles().clientLevel(clientObjectId).add(Arrays.asList(
                        readRole,
                        writeRole
                    ));
                    LOG.info("Assigned all application roles to 'admin' user");
                }
            );

        // Find out if there is a 'test' user already present, delete it
        fromCallable(() -> usersResource.search("test", null, null, null, null, null))
            .flatMap(Observable::from)
            .map(userRepresentation -> usersResource.get(userRepresentation.getId()))
            .subscribe(UserResource::remove);

        // Create a new 'test' user with 'read' role
        UserRepresentation testUser = new UserRepresentation();
        testUser.setUsername("test");
        testUser.setFirstName("Testuserfirst");
        testUser.setLastName("Testuserlast");
        testUser.setEnabled(true);
        usersResource.create(testUser);
        testUser = usersResource.search("test", null, null, null, null, null).get(0);

        CredentialRepresentation testUserCredential = new CredentialRepresentation();
        testUserCredential.setType("password");
        testUserCredential.setValue("test");
        testUserCredential.setTemporary(false);
        usersResource.get(testUser.getId()).resetPassword(testUserCredential);

        LOG.info("Added user '" + testUser.getUsername() + "' with password '" + testUserCredential.getValue() + "'");

        // Add mapping for client role 'read' to user 'test'
        usersResource.get(testUser.getId()).roles().clientLevel(clientObjectId).add(Arrays.asList(
            readRole,
            writeRole
        ));

    }
}
