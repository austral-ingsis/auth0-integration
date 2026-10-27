# Auth0 Integration Demo Service

This Spring Boot service shows how to protect endpoints with Auth0 access tokens (JWT), acting as an
OAuth2 **resource server**.

Requirements: JDK 21 (Gradle downloads it automatically if it's missing). Run with `./gradlew bootRun`,
test with `./gradlew test`.

For a step-by-step walkthrough (in Spanish) of everything the service shows, see [DEMO.md](DEMO.md).

## Auth0 setup

You need an Auth0 account and a tenant. The resources this demo uses are:

- an **API** (resource server) with the permission `read:snippets` and **RBAC enabled**. Without RBAC,
  Auth0 grants *any* scope defined on the API to *any* user who asks for it, so the scope check on
  `/scopedAuthenticate` would protect nothing;
- a **role** `snippet-reader` with that permission;
- an **application** (the client that asks for tokens);
- **two users**: one with the role and one without it.

### Option A: with Pulumi (recommended)

The `setup/` folder declares all of the above as code ([Pulumi](https://www.pulumi.com/) + TypeScript):
`npm run up` creates it and `npm run destroy` deletes it, configured from `setup/.env`. Follow the step-by-step walkthrough in
[`setup/README.md`](setup/README.md), which ends with the `.env` for this service filled in.

### Option B: by hand in the dashboard

1. Create an **API** (Applications → APIs). Its *Identifier* is the **audience**.
2. In the API → *Permissions*, add the permission `read:snippets`.
3. In the API → *Settings*, turn on **Enable RBAC**.
4. Create an **Application** (Regular Web Application). You only need its Client ID.
5. Create two users.
6. Create a **Role** (User Management → Roles), give it the `read:snippets` permission from your API,
   and assign it to one of the two users.

Then copy `.env.example` to `.env` and fill it in: `AUTH0_DOMAIN` is your tenant domain (without
`https://`), `AUTH0_AUDIENCE` the API Identifier from step 1 and `AUTH0_APP_CLIENT_ID` the application's
Client ID.

The service reads `.env` on startup. Regular environment variables with the same names also work, and win
over the file.

## How to use

To call the API without a frontend you need an access token. Get one with the
[Auth0 CLI](https://github.com/auth0/auth0-cli): it opens the real Auth0 login page in your browser
(Authorization Code flow), so the password never goes through a terminal command.

```shell
brew install auth0/auth0-cli/auth0    # other platforms: see the Auth0 CLI README
auth0 login                           # once: links the CLI to your tenant

set -a; source .env; set +a          # loads the .env values into your shell
auth0 test token "$AUTH0_APP_CLIENT_ID" --audience "$AUTH0_AUDIENCE" --scopes read:snippets
```

If you created the application by hand, the first time the CLI asks to add `http://localhost:8484` to its
Allowed Callback URLs: accept (the Pulumi setup already includes it). Log in as one of your users, copy the `access_token` and call the API:

```shell
export TOKEN=<access_token>
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/snippets
```

You can paste the token into [jwt.io](https://jwt.io) to see its `iss`, `aud`, `sub` and `scope` claims.

Postman and the IntelliJ HTTP Client can also get the token for you: both support OAuth 2.0 Authorization
Code + PKCE (set the Auth URL to `https://<AUTH0_DOMAIN>/authorize?audience=<AUTH0_AUDIENCE>` and the
Token URL to `https://<AUTH0_DOMAIN>/oauth/token`).

## Trying out scope-based authorization

`/scopedAuthenticate` requires the `read:snippets` scope (in Spring: the `SCOPE_read:snippets` authority).
Request tokens with and without `--scopes read:snippets`, and log in as the user with and without the role:
with RBAC enabled, Auth0 only puts the scope in the token when the user has that permission *and* the
request asked for it.

To switch users, first open `https://<AUTH0_DOMAIN>/v2/logout` in the browser. Otherwise Auth0 reuses the
session of the last user and doesn't show the login page again.

## Trying out ownership-based authorization

The scope says what the app can do on the API, not *which* snippet. For that, `/owned-snippets` checks an
attribute of the resource: only the user who created a snippet (the token's `sub`) can read it.

Get a token for each user (from `setup/`, see `setup/README.md`):

```shell
export A=$(npm run --silent token)    # log in as reader@example.com
export B=$(npm run --silent token)    # log in as no-role@example.com
```

```shell
# A creates a snippet; the response has its id
curl -s -H "Authorization: Bearer $A" -H 'Content-Type: text/plain' -d 'my secret' http://localhost:8080/owned-snippets

curl -i -H "Authorization: Bearer $A" http://localhost:8080/owned-snippets/<id>   # 200: A is the owner
curl -i -H "Authorization: Bearer $B" http://localhost:8080/owned-snippets/<id>   # 403: B isn't
```

## What the service validates

Configured in `application.yml`, applied by Spring Security on every request:

- the signature, using Auth0's public keys (JWKS), discovered from the issuer
- `exp` / `nbf` (the token isn't expired)
- `iss` equals `https://<AUTH0_DOMAIN>/`
- `aud` contains `AUTH0_AUDIENCE`
