# Auth0 Integration Demo Service

This Spring Boot service showcases how to use Auth0 to protect endpoints.

## Pre-Requisites

1. Have an Auth0 account
2. Have an Auth0 application and an Auth0 API
3. Have a created user with 2 roles: "read:snippets" and "write:snippets". You must create them in the API first as they are linked to it.
4. Allow the Auth0 application to use the "password" Grant-Type
5. Set a database connection default for the tenant.

With this resources created, you will have to set the following environment variables:

- `AUTH0_AUDIENCE`: use the "audience" value you set when first creating the Auth0 API
- `AUTH_SERVER_URI`: use the url set to you as Auth0 tenant. It will have the following format: "dev-****.us.auth0.com"
- `AUTH_CLIENT_ID`: use the client ID in your "API" Application (when you created the Auth0 API it also created an Application for you)
- `AUTH_CLIENT_SECRET`: use the client secret in your "API" Application (when you created the Auth0 API it also created an Application for you)

## How to use

First you need to get an Auth Token in behalf of the Resource Owner (the user you created).
To do this you can run the following curl:

```shell
curl --location '<AUTH_SERVER_URI>' \
--data-urlencode 'grant_type=password' \
--data-urlencode 'username=<your users email>' \
--data-urlencode 'password=<your users password>' \
--data-urlencode 'audience=<the Auth0 adience>' \
--data-urlencode 'scope=read:snippets write:snippets' \
--data-urlencode 'client_id=<the AUTH0 Application Client ID (not the API)>' \
--data-urlencode 'client_secret=<the AUTH0 Application Client ID (not the API)>'
```

Grab the "access_token" field in the response and use it as Bearer token for your requests.

## Trying out role based auth

In the section above we requested an auth token with both "read" and "write" scoped. Try requesting only "read" scopes
and Posting a resource to the server and the other way around.