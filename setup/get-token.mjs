// Prints an access token for the demo API, logging in through the real Auth0 login page.
//
//   npm run token                      # asks for the read:snippets scope
//   npm run token -- "read:snippets"   # any space-separated scopes
//   npm run token -- ""                # no scopes
//   export TOKEN=$(npm run --silent token)
//
// Flow: Authorization Code + PKCE (what a frontend or native app does). The token goes to stdout;
// everything else to stderr, so it can be captured with $(...).

import { execFileSync, spawn } from "node:child_process";
import { createHash, randomBytes } from "node:crypto";
import { createServer } from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const log = (...args) => console.error(...args);

// Domain, audience and client come from the Pulumi stack outputs
function stackOutputs() {
    const pulumi = path.join(here, "node_modules", "pulumi", "run.js");
    try {
        const json = execFileSync(process.execPath, [pulumi, "stack", "output", "--json"], {
            cwd: here,
            encoding: "utf8",
            stdio: ["ignore", "pipe", "inherit"],
        });
        return JSON.parse(json);
    } catch {
        log("Could not read the Pulumi outputs. Did you run `npm run up`?");
        process.exit(1);
    }
}

const outputs = stackOutputs();
const domain = outputs.AUTH0_DOMAIN;
const audience = outputs.AUTH0_AUDIENCE;
const clientId = outputs.tokenScriptClientId;
const redirectUri = outputs.tokenScriptCallbackUrl;
if (!clientId || !redirectUri) {
    log("The stack has no get-token application yet: run `npm run up` first.");
    process.exit(1);
}
const scope = process.argv[2] ?? "read:snippets";

// PKCE: a random secret (verifier) stays here; only its hash (challenge) goes to the browser
const base64url = (buffer) => buffer.toString("base64url");
const verifier = base64url(randomBytes(32));
const challenge = base64url(createHash("sha256").update(verifier).digest());
const state = base64url(randomBytes(16));

const authorizeUrl = new URL(`https://${domain}/authorize`);
authorizeUrl.search = new URLSearchParams({
    response_type: "code",
    client_id: clientId,
    redirect_uri: redirectUri,
    audience,
    ...(scope ? { scope } : {}),
    code_challenge: challenge,
    code_challenge_method: "S256",
    state,
    prompt: "login", // always show the login page, so you can pick the user
}).toString();

const callback = new URL(redirectUri);

const server = createServer(async (req, res) => {
    const url = new URL(req.url, redirectUri);
    if (url.pathname !== callback.pathname) {
        res.writeHead(404).end();
        return;
    }
    const finish = (status, message) => {
        res.writeHead(status, { "Content-Type": "text/plain; charset=utf-8" })
            .end(message, () => server.closeAllConnections());
        server.close();
    };

    const error = url.searchParams.get("error");
    if (error) {
        finish(400, "Login failed. Check the terminal.");
        log(`Login failed: ${error}: ${url.searchParams.get("error_description")}`);
        process.exitCode = 1;
        return;
    }
    if (url.searchParams.get("state") !== state) {
        finish(400, "Unexpected state. Check the terminal.");
        log("The callback's state doesn't match: ignoring it.");
        process.exitCode = 1;
        return;
    }

    // Exchange the one-time code (+ the PKCE verifier) for the tokens
    let response;
    try {
        response = await fetch(`https://${domain}/oauth/token`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                grant_type: "authorization_code",
                client_id: clientId,
                code: url.searchParams.get("code"),
                code_verifier: verifier,
                redirect_uri: redirectUri,
            }),
        });
    } catch (err) {
        finish(502, "Could not reach Auth0. Check the terminal.");
        log(`Could not reach https://${domain}/oauth/token: ${err.message}`);
        process.exitCode = 1;
        return;
    }
    const body = await response.json();
    if (!response.ok) {
        finish(500, "Could not get the token. Check the terminal.");
        log(`Token request failed (${response.status}):`, body);
        process.exitCode = 1;
        return;
    }

    finish(200, "Done. You can close this tab and go back to the terminal.");

    const claims = JSON.parse(Buffer.from(body.access_token.split(".")[1], "base64url").toString());
    log("\nAccess token claims:");
    log(`  sub:   ${claims.sub}`);
    log(`  aud:   ${[].concat(claims.aud).join(", ")}`);
    log(`  scope: ${claims.scope ?? "(none)"}`);
    log(`  exp:   ${new Date(claims.exp * 1000).toLocaleString()}\n`);
    console.log(body.access_token);
});

server.on("error", (err) => {
    log(`Could not listen on ${callback.host}: ${err.message}`);
    process.exit(1);
});

server.listen(Number(callback.port), "localhost", () => {
    log("Opening the Auth0 login page in your browser. If it doesn't open, go to:\n");
    log(`  ${authorizeUrl}\n`);
    const opener = { darwin: "open", win32: "explorer" }[process.platform] ?? "xdg-open";
    spawn(opener, [authorizeUrl.toString()], { stdio: "ignore", detached: true })
        .on("error", () => {})
        .unref();
});

// Give up if nobody logs in
setTimeout(() => {
    log("Timed out waiting for the login (2 minutes).");
    process.exit(1);
}, 120_000).unref();
