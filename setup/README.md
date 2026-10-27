# Auth0 setup with Pulumi

This folder creates, as code, everything the demo service needs in Auth0. Instead of clicking through
the dashboard, you run `npm run up` and get the same tenant every time; `npm run destroy` cleans it up.

## What it creates

| Resource | Name | Why |
|---|---|---|
| API (resource server) | `auth0-integration-demo`, identifier `https://auth0-integration-demo` | The service. Its identifier is the token's `aud` |
| Permission (scope) | `read:snippets` | What `/scopedAuthenticate` asks for |
| RBAC on the API | enabled | Auth0 only grants the scope to users who have the permission |
| Role | `snippet-reader` | Holds the `read:snippets` permission |
| Application | `auth0-integration-demo` (Regular Web App) | The client that asks for tokens. Its callback `http://localhost:8484` is the one the Auth0 CLI uses |
| Application | `auth0-integration-demo (get-token script)` (Native) | A public client (no secret) for `npm run token`, with callback `http://localhost:8485/callback` |
| Connection access | `Username-Password-Authentication` → the application | Lets the app log users in with the tenant's default user database |
| User | `reader@example.com`, with the `snippet-reader` role | Gets the scope: 200 on `/scopedAuthenticate` |
| User | `no-role@example.com`, no role | Doesn't get the scope: 403 on `/scopedAuthenticate` |

All of it is in [`index.ts`](index.ts).

## Requirements

- An Auth0 account and a tenant (a free one is enough)
- Node.js 20.12 or newer (the Pulumi CLI comes with `npm install`)
- Optional: the [Auth0 CLI](https://github.com/auth0/auth0-cli), as an alternative to `npm run token`

## 1. Give Pulumi access to your tenant (once, by hand)

Pulumi creates the resources through Auth0's Management API, so it needs its own credentials. This is
the only step you can't do with Pulumi.

1. In the Auth0 dashboard: **Applications → Applications → Create Application**.
2. Name it `pulumi`, choose **Machine to Machine Applications** and click *Create*.
3. Select the **Auth0 Management API**, click **Select all** in the permissions and then *Authorize*.
   (All permissions is fine for a dev tenant.)
4. In the app's **Settings** tab, copy the **Domain**, **Client ID** and **Client Secret**.

## 2. Fill in `setup/.env`

All the configuration lives in `setup/.env` (git-ignored). Start from the example:

```shell
cd setup
cp .env.example .env
```

| Variable | Value |
|---|---|
| `AUTH0_DOMAIN` | The tenant domain from step 1.4, without `https://` |
| `AUTH0_CLIENT_ID` / `AUTH0_CLIENT_SECRET` | The `pulumi` app's credentials from step 1.4. The Auth0 provider reads these exact names |
| `DEMO_USER_PASSWORD` | Password for both demo users. It must satisfy the tenant's password policy (by default: 8+ characters with lowercase, uppercase and numbers), or Auth0 rejects the users |
| `PULUMI_CONFIG_PASSPHRASE` | Anything you like. It encrypts the secrets in Pulumi's state; keep it, every later command needs the same one |
| `AUTH0_AUDIENCE` (optional) | The API identifier. Default: `https://auth0-integration-demo` |
| `AUTH0_CONNECTION_NAME` (optional) | The database connection for the users. Default: `Username-Password-Authentication` |

## 3. Install and create the stack

```shell
npm install
npm run init
```

- `npm install` also installs the Pulumi CLI, as a dev dependency. The first run downloads its binary to
  `~/.pulumi/versions/`.
- `npm run init` creates the `dev` *stack*: one instance of this setup. Pulumi keeps its *state* there,
  the record of what it created, so it knows what to change or delete later.
- No Pulumi account or `pulumi login` needed: `Pulumi.yaml` sets the backend to `file://.`, so the state
  goes to `setup/.pulumi/` (git-ignored) instead of Pulumi Cloud.

### The npm scripts

Every script loads `setup/.env` and runs the Pulumi CLI from `node_modules` (no `npx`, no global install):

| Script | Runs |
|---|---|
| `npm run init` | `pulumi stack select --create dev` |
| `npm run preview` | `pulumi preview` |
| `npm run up` | `pulumi up` |
| `npm run refresh` | `pulumi refresh` |
| `npm run destroy` | `pulumi destroy` |
| `npm run outputs` | `pulumi stack output` |
| `npm run token` | `get-token.mjs`: logs in and prints an access token (see step 6) |
| `npm run pulumi -- <args>` | any other command, e.g. `npm run pulumi -- stack ls` |

Run them from `setup/`. Calling `pulumi` directly skips the `.env`.

## 4. Preview and create the resources

```shell
npm run preview
```

`preview` shows what Pulumi *would* do, without touching Auth0. The first time you should see the
resources from the table above, all marked `create`.

```shell
npm run up
```

`up` shows the same preview and asks for confirmation. Choose `yes`. At the end it prints the outputs:

```
Outputs:
    AUTH0_AUDIENCE: "https://auth0-integration-demo"
    AUTH0_DOMAIN  : "dev-xxxx.us.auth0.com"
    clientId      : "AbC123..."
    users         : {
        withRole   : "reader@example.com"
        withoutRole: "no-role@example.com"
    }
```

Open the dashboard: the API, the role, the application and the users are there.

## 5. Connect the service

Run `npm run outputs` to see the outputs again. Copy the root `.env.example` to `.env` (in the project
root, not in `setup/`) and fill it in:

| `.env` variable | Output |
|---|---|
| `AUTH0_DOMAIN` | `AUTH0_DOMAIN` |
| `AUTH0_AUDIENCE` | `AUTH0_AUDIENCE` |
| `AUTH0_APP_CLIENT_ID` | `clientId` |

Then start the service from the project root:

```shell
./gradlew bootRun
```

## 6. Try it

### Get a token

From `setup/`:

```shell
export TOKEN=$(npm run --silent token)
```

The script opens the Auth0 login page in your browser. Log in as `reader@example.com` (password:
`DEMO_USER_PASSWORD`). The first time, Auth0 asks the user to authorize the app: accept. Back in the
terminal, the script prints the token's claims and `TOKEN` has the token.

How it works ([`get-token.mjs`](get-token.mjs), no dependencies): it's the Authorization Code + PKCE
flow, the same one a frontend uses. It opens `/authorize`, receives the one-time code on
`http://localhost:8485/callback`, and exchanges it (with the PKCE verifier) for the access token. It
always shows the login page (`prompt=login`), so you can pick a different user each time.

Variants:

```shell
npm run token                          # prints the claims and the token
npm run --silent token -- ""           # no scopes
npm run --silent token -- "read:snippets other:scope"
```

Alternatively, with the [Auth0 CLI](https://github.com/auth0/auth0-cli), from the project root:
`set -a; source .env; set +a` and then
`auth0 test token "$AUTH0_APP_CLIENT_ID" --audience "$AUTH0_AUDIENCE" --scopes read:snippets`.

### Call the service

With the service running (`./gradlew bootRun` in the project root):

```shell
curl -i http://localhost:8080/snippets                                              # 401: no token
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/snippets            # 200
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/scopedAuthenticate  # 200
```

Now get a token as `no-role@example.com` and repeat:

```shell
export TOKEN=$(npm run --silent token)
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/snippets            # 200
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/scopedAuthenticate  # 403
```

Compare both tokens' claims (the script prints them, or paste the tokens into [jwt.io](https://jwt.io)):
the first has `scope: read:snippets`, the second doesn't, even though both asked for it. That's RBAC.

## Changing the setup

Edit `index.ts`, then run `npm run preview` to see the diff and `npm run up` to apply it. Pulumi only
changes what's different.

If someone changes these resources in the dashboard, the next `npm run up` puts them back the way
`index.ts` says. `npm run refresh` first reads the current state from Auth0 and shows the differences.

## Deleting everything

```shell
npm run destroy                     # deletes the resources from Auth0
npm run pulumi -- stack rm dev      # deletes the stack and its state (optional)
```

The `pulumi` Machine to Machine app from step 1 isn't managed by Pulumi: delete it by hand if you don't
need it anymore.

## Troubleshooting

| Error | Cause and fix |
|---|---|
| `Missing AUTH0_DOMAIN in setup/.env` (or another variable) | Fill it in `setup/.env` (step 2) |
| `node: .env: not found` | There's no `setup/.env`: copy it from `.env.example` (step 2), and run the scripts from `setup/` |
| `401 Unauthorized` / `Insufficient scope` from the Management API | The `pulumi` app isn't authorized for the Management API, or is missing permissions: repeat step 1.3 |
| `PasswordStrengthError` creating the users | The password doesn't meet the policy: change `DEMO_USER_PASSWORD` in `setup/.env` and run `npm run up` again |
| `The user already exists` | A user with that email was created by hand: delete it in the dashboard and run `npm run up` again |
| `no connection found with name` | The tenant doesn't have `Username-Password-Authentication`: set `connectionName` to an existing database connection |
| `Could not listen on localhost:8485` | Another `npm run token` is still running, or something else uses the port: close it |
| `Callback URL mismatch` on login | The application's callbacks don't include `http://localhost:8484`: run `npm run up` to restore them |
| `incorrect passphrase` | `PULUMI_CONFIG_PASSPHRASE` in `setup/.env` changed since `npm run init`: put the original back |
