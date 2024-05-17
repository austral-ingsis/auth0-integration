# Auth0 Integration Demo Service

This Spring Boot service showcases how to use Auth0 to protect endpoints.

## Pre-Requisites

1. Have an Auth0 account
2. Have an Auth0 application and an Auth0 API
3. Have a created user.
4. (Optional): attach a role to the user: "read:snippets". You must create them in the API first as they are linked to it.
5. Allow the Auth0 application to use the "password" Grant-Type
6. Set a database connection default for the tenant.

With this resources created, you will have to set the following environment variables:

- `AUTH0_AUDIENCE`: use the "audience" value you set when first creating the Auth0 API
- `AUTH_SERVER_URI`: use the url set to you as Auth0 tenant. It will have the following format: "dev-****.us.auth0.com"

## How to use

First you need to get an Auth Token in behalf of the Resource Owner (the user you created).
To do this you can run the following curl (you can also import this request in Postman):

```shell
curl --location '<AUTH_SERVER_URI>' \
--data-urlencode 'grant_type=password' \
--data-urlencode 'username=<your users email>' \
--data-urlencode 'password=<your users password>' \
--data-urlencode 'audience=<the Auth0 audience>' \
--data-urlencode 'scope=read:snippets' \ // This line is optional
--data-urlencode 'client_id=<the AUTH0 Application Client ID (not the API)>'
```

Grab the "access_token" field in the response and use it as Bearer token for your requests.

**Note**: this request uses the insecure "password" grant. We won't be using this grant for other use cases than this
and you shouldn't use it in your Public facing apps.

## Trying out role based auth

In the pre-requisites section, you were asked to optionally, create and attach a "read:snippets" role to the user.
Try requesting the token with a specific scope and without to see what happens with the endpoint.