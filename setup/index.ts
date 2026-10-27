import * as pulumi from "@pulumi/pulumi";
import * as auth0 from "@pulumi/auth0";

// Everything comes from setup/.env (see .env.example), which the npm scripts load.
// The Auth0 provider reads AUTH0_DOMAIN, AUTH0_CLIENT_ID and AUTH0_CLIENT_SECRET from there on its own.
function requireEnv(name: string): string {
    const value = process.env[name];
    if (!value) {
        throw new Error(`Missing ${name} in setup/.env (see .env.example)`);
    }
    return value;
}

const domain = requireEnv("AUTH0_DOMAIN");
const audience = process.env.AUTH0_AUDIENCE || "https://auth0-integration-demo";
const userPassword = pulumi.secret(requireEnv("DEMO_USER_PASSWORD"));

// Port used by `auth0 test token` to receive the login callback
const cliCallback = "http://localhost:8484";

// --- API (resource server) -------------------------------------------------

const api = new auth0.ResourceServer("api", {
    name: "auth0-integration-demo",
    identifier: audience,
    signingAlg: "RS256",
    // "Enable RBAC": a scope is only granted if the user has that permission
    enforcePolicies: true,
    skipConsentForVerifiableFirstPartyClients: true,
});

const apiScopes = new auth0.ResourceServerScopes("api-scopes", {
    resourceServerIdentifier: api.identifier,
    scopes: [{ name: "read:snippets", description: "Read snippets" }],
});

// --- Role ------------------------------------------------------------------

const reader = new auth0.Role("snippet-reader", {
    name: "snippet-reader",
    description: "Can read snippets",
});

new auth0.RolePermissions("snippet-reader-permissions", {
    roleId: reader.id,
    permissions: [{ name: "read:snippets", resourceServerIdentifier: api.identifier }],
}, {
    // The permission has to exist on the API before it can be given to the role
    dependsOn: [apiScopes],
});

// --- Application (the client that asks for tokens) -------------------------

const app = new auth0.Client("app", {
    name: "auth0-integration-demo",
    appType: "regular_web",
    grantTypes: ["authorization_code", "refresh_token"],
    callbacks: [cliCallback],
    oidcConformant: true,
});

// --- Users -----------------------------------------------------------------

// The database connection every new tenant comes with (the "Username-Password" one)
const connectionName = process.env.AUTH0_CONNECTION_NAME || "Username-Password-Authentication";
const connection = auth0.getConnectionOutput({ name: connectionName });

// Make sure the app can log users in with that connection
new auth0.ConnectionClient("app-connection", {
    connectionId: connection.id,
    clientId: app.id,
});

const userWithRole = new auth0.User("reader", {
    connectionName,
    email: "reader@example.com",
    password: userPassword,
});

new auth0.UserRoles("reader-roles", {
    userId: userWithRole.id,
    roles: [reader.id],
});

const userWithoutRole = new auth0.User("no-role", {
    connectionName,
    email: "no-role@example.com",
    password: userPassword,
});

// --- Application for get-token.mjs ------------------------------------------

// A public client (no secret) for the get-token script: it logs in with Authorization Code + PKCE
// and receives the code on a local callback, like a native app would.
const tokenScriptCallback = "http://localhost:8485/callback";

const tokenScriptApp = new auth0.Client("token-script", {
    name: "auth0-integration-demo (native)",
    appType: "native",
    grantTypes: ["authorization_code"],
    callbacks: [tokenScriptCallback],
    oidcConformant: true,
});

new auth0.ConnectionClient("token-script-connection", {
    connectionId: connection.id,
    clientId: tokenScriptApp.id,
});

// --- Outputs: what the Spring app and the Auth0 CLI need -------------------

export const AUTH0_DOMAIN = domain;
export const AUTH0_AUDIENCE = api.identifier;
export const clientId = app.clientId;
export const tokenScriptClientId = tokenScriptApp.clientId;
export const tokenScriptCallbackUrl = tokenScriptCallback;
export const users = {
    withRole: userWithRole.email,
    withoutRole: userWithoutRole.email,
};
