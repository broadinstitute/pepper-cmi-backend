package org.broadinstitute.ddp.util;

import com.auth0.json.mgmt.client.Client;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.security.AesUtil;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Auth0ClientMigrator {


    private Auth0ManagementClient oldMgmtClient;

    private Auth0ManagementClient newMgmtClient;

    private String encryptionKey;

    public Auth0ClientMigrator(String oldDomain,
                               String oldMgmtClientId,
                               String oldMgmtClientSecret,
                               String newDomain,
                               String newMgmtClientId,
                               String newMgmtSecret,
                               String encryptionKey) {
        oldMgmtClient = new Auth0ManagementClient(oldDomain, oldMgmtClientId, oldMgmtClientSecret);
        newMgmtClient = new Auth0ManagementClient(newDomain, newMgmtClientId, newMgmtSecret);
        this.encryptionKey = encryptionKey;
    }

    public static void main(String[] args) {
        String oldDomain = args[0];
        String oldMgmtClientId = args[1];
        String oldMgmtClientSecret = args[2];
        String newDomain = args[3];
        String newMgmtClientId = args[4];
        String newMgmtClientSecret = args[5];
        String encryptionKey = args[6];

        var migrator = new Auth0ClientMigrator(oldDomain, oldMgmtClientId, oldMgmtClientSecret,
                newDomain, newMgmtClientId, newMgmtClientSecret, encryptionKey);

        var replacements = migrator.buildClientReplacements();

        for (ClientReplacement replacement : replacements) {
            log.info("{} with old id {} and secret {} will use new id {} and secret {} encrypted {}",
                    replacement.getClientName(), replacement.getOldClientId(), replacement.getOldClientSecret(),
                    replacement.getNewClientId(), replacement.getNewClientSecret(),
                    replacement.getEncryptedNewClientSecret());
            System.out.println("-- update " + replacement.getClientName() + " to key " + replacement.getNewClientId()
                            + " secret " + replacement.getNewClientSecret() + "\n"
                            + "-- domain " + newDomain + "\n"
                            + "update client set auth0_client_id = '" + replacement.getNewClientId() + "',\n "
                            + " auth0_signing_secret = '" + replacement.getEncryptedNewClientSecret() + "'\n "
                            + " where auth0_client_id = '" + replacement.getOldClientId() + "';\n");
        }

        System.out.printf("-- new management client id " + newMgmtClientId + " secret " + newMgmtClientSecret);
        System.exit(0);
    }

    public List<ClientReplacement> buildClientReplacements() {
        var oldClients = oldMgmtClient.listClients();
        var newClients = newMgmtClient.listClients();
        List<ClientReplacement> replacements = new ArrayList<>();
        var unresolvedClients = new ArrayList<String>();

        for (Client oldClient : oldClients) {
            boolean foundIt = false;
            for (Client newClient : newClients) {
                if (oldClient.getName().equals(newClient.getName())) {
                    replacements.add(new ClientReplacement(oldClient.getName(),
                            oldClient.getClientId(),
                            oldClient.getClientSecret(),
                            newClient.getClientId(),
                            newClient.getClientSecret(),
                            AesUtil.encrypt(newClient.getClientSecret(), encryptionKey)));
                    foundIt = true;
                    break;
                }
            }
            if (!foundIt) {
                unresolvedClients.add(oldClient.getName());
            }
        }

        if (!unresolvedClients.isEmpty()) {
            log.info("Could not resolve {} new clients.", unresolvedClients.size());
            for (String unresolvedClient : unresolvedClients) {
                log.info("Could not resolve new client {}", unresolvedClient);
            }
        }
        return replacements;
    }

    private static class ClientReplacement {

        private String oldClientId;

        private String oldClientSecret;

        private String clientName;

        private String newClientId;

        private String newClientSecret;
        private String encryptedNewClientSecret;

        public ClientReplacement(String clientName, String oldClientId, String oldClientSecret,
                                 String newClientId, String newClientSecret, String encryptedNewClientSecret) {
            this.clientName = clientName;
            this.oldClientId = oldClientId;
            this.oldClientSecret = oldClientSecret;
            this.newClientId = newClientId;
            this.newClientSecret = newClientSecret;
            this.encryptedNewClientSecret = encryptedNewClientSecret;
        }

        public String getEncryptedNewClientSecret() {
            return encryptedNewClientSecret;
        }

        public String getOldClientId() {
            return oldClientId;
        }

        public String getOldClientSecret() {
            return oldClientSecret;
        }

        public String getClientName() {
            return clientName;
        }

        public String getNewClientId() {
            return newClientId;
        }

        public String getNewClientSecret() {
            return newClientSecret;
        }
    }

}
