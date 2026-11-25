/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/

// Kodra-specific build tooling: smoke tests a packaged Kodra server before it is
// published as an artifact. This file is not part of the upstream build.

/**
 * Smoke tests a packaged Kodra server directory (the output of `vscode-reh-<platform>-<arch>`).
 *
 * The checks cover the Stage 1 claims that are only observable at runtime:
 *
 * - the packaged `bin/<serverApplicationName>` launcher starts the server, binds a loopback
 *   address and picks a port from `--port 0`;
 * - the loopback bind is not reachable through the runner's non-internal interface;
 * - with a connection token file, unauthenticated requests are rejected and the token grants access;
 * - the REH-web package serves the Kodra workbench HTML and the web manifest from the package,
 *   and that HTML references no Microsoft-hosted origin;
 * - the REH package serves no workbench HTML at all, and reports its commit from `/version`.
 *
 * Usage: node build/kodra/smokeTestPackagedServer.ts <packaged-server-root>
 */

import { spawn, type ChildProcess } from 'child_process';
import * as fs from 'fs';
import * as http from 'http';
import * as net from 'net';
import * as os from 'os';
import * as path from 'path';
import { setTimeout as delay } from 'timers/promises';

const startupTimeoutMs = 90_000;
const requestTimeoutMs = 10_000;
const connectionToken = 'kodra-ci-token_0';
const boundPattern = /Server bound to (?<address>\S+):(?<port>\d+) \((?<family>[^)]+)\)/;

const forbiddenOrigins = [
	/marketplace\.visualstudio\.com/i,
	/update\.code\.visualstudio\.com/i,
	/vscode-cdn\.net/i,
	/vscode-sync\.azurewebsites\.net/i,
];

interface HttpResult {
	readonly status: number;
	readonly headers: http.IncomingHttpHeaders;
	readonly body: string;
}

interface Bound {
	readonly address: string;
	readonly port: number;
}

async function main(serverRoot: string | undefined): Promise<void> {
	if (!serverRoot) {
		throw new Error('Usage: node build/kodra/smokeTestPackagedServer.ts <packaged-server-root>');
	}

	const root = path.resolve(serverRoot);
	const product = JSON.parse(fs.readFileSync(path.join(root, 'product.json'), 'utf8'));
	const nodePath = path.join(root, process.platform === 'win32' ? 'node.exe' : 'node');
	const launcherPath = path.join(root, 'bin', product.serverApplicationName);
	for (const requiredPath of [nodePath, launcherPath]) {
		if (!fs.existsSync(requiredPath)) {
			throw new Error(`Packaged server file not found: ${requiredPath}`);
		}
	}

	const hasWebClient = fs.existsSync(path.join(root, 'out', 'vs', 'code', 'browser', 'workbench', 'workbench.html'));
	console.log(`Package ${path.basename(root)} ${product.version ?? ''}: ${hasWebClient ? 'ships the web client' : 'has no web client'}.`);

	const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'kodra-server-smoke-'));
	const tokenPath = path.join(temporaryRoot, 'token');
	const dataDir = path.join(temporaryRoot, 'server-data');
	const userDataDir = path.join(temporaryRoot, 'user-data');
	fs.writeFileSync(tokenPath, `${connectionToken}\n`);
	fs.mkdirSync(dataDir, { recursive: true });
	fs.mkdirSync(userDataDir, { recursive: true });

	const child = spawn(launcherPath, [
		'--host', '127.0.0.1',
		'--port', '0',
		'--connection-token-file', tokenPath,
		'--server-data-dir', dataDir,
		'--user-data-dir', userDataDir,
		'--disable-telemetry',
		'--accept-server-license-terms',
	], {
		cwd: root,
		env: { ...process.env, VSCODE_DEV: undefined },
		stdio: ['ignore', 'pipe', 'pipe'],
	});

	try {
		const bound = await waitForReady(child);
		console.log(`Packaged server listening on ${bound.address}:${bound.port}.`);

		verifyBoundAddress(bound);
		await verifyLoopbackOnly(bound.port);
		await verifyVersionEndpoint(bound.port, product.commit);
		await verifyTokenEnforcement(bound.port);

		if (hasWebClient) {
			await verifyWebClient(bound.port);
		} else {
			await verifyNoWebClient(bound.port);
		}

		console.log('Packaged server smoke test passed.');
	} finally {
		await stopProcess(child);
		fs.rmSync(temporaryRoot, { recursive: true, force: true });
	}
}

function waitForReady(child: ChildProcess): Promise<Bound> {
	return new Promise((resolve, reject) => {
		let output = '';
		let settled = false;

		const finish = (error?: Error, bound?: Bound) => {
			if (settled) {
				return;
			}
			settled = true;
			clearTimeout(timeout);
			if (error) {
				reject(error);
			} else {
				resolve(bound!);
			}
		};

		const appendOutput = (data: Buffer) => {
			const text = data.toString();
			process.stdout.write(text);
			output = `${output}${text}`.slice(-64 * 1024);
			const bound = boundPattern.exec(output);
			if (bound?.groups) {
				finish(undefined, { address: bound.groups.address, port: Number(bound.groups.port) });
			}
		};

		child.stdout?.on('data', appendOutput);
		child.stderr?.on('data', appendOutput);
		child.once('error', error => finish(error));
		child.once('exit', (code, signal) => {
			finish(new Error(`Packaged server exited before becoming ready (code: ${code}, signal: ${signal}).\n${output}`));
		});

		const timeout = setTimeout(() => {
			finish(new Error(`Timed out after ${startupTimeoutMs}ms waiting for the packaged server to start.\n${output}`));
		}, startupTimeoutMs);
	});
}

async function request(port: number, pathname: string, headers: http.OutgoingHttpHeaders = {}): Promise<HttpResult> {
	return new Promise((resolve, reject) => {
		const req = http.request({
			host: '127.0.0.1',
			port,
			path: pathname,
			method: 'GET',
			headers,
			timeout: requestTimeoutMs,
		}, res => {
			const chunks: Buffer[] = [];
			res.on('data', (chunk: Buffer) => chunks.push(chunk));
			res.on('end', () => {
				resolve({
					status: res.statusCode ?? 0,
					headers: res.headers,
					body: Buffer.concat(chunks).toString('utf8'),
				});
			});
		});
		req.on('timeout', () => req.destroy(new Error(`Timed out requesting ${pathname}.`)));
		req.on('error', reject);
		req.end();
	});
}

function verifyBoundAddress(bound: Bound): void {
	if (bound.address !== '127.0.0.1' && bound.address !== '::1') {
		throw new Error(`Packaged server bound to ${bound.address}:${bound.port}, expected a loopback address.`);
	}
	if (bound.port <= 0) {
		throw new Error(`Packaged server did not report a real port for --port 0: ${bound.port}.`);
	}
	console.log(`Server bound to loopback ${bound.address} on the port chosen for --port 0.`);
}

async function verifyLoopbackOnly(port: number): Promise<void> {
	const external = Object.values(os.networkInterfaces())
		.flat()
		.find(address => address && address.family === 'IPv4' && !address.internal);

	if (!external) {
		console.log('Loopback-only check skipped: this runner has no non-internal IPv4 address.');
		return;
	}

	const failure = await new Promise<NodeJS.ErrnoException | undefined>(resolve => {
		const socket = net.connect({ host: external.address, port, timeout: 3_000 }, () => {
			socket.destroy();
			resolve(undefined);
		});
		socket.on('timeout', () => {
			socket.destroy();
			resolve(Object.assign(new Error('timeout'), { code: 'ETIMEDOUT' }));
		});
		socket.on('error', error => resolve(error as NodeJS.ErrnoException));
	});

	if (!failure) {
		throw new Error(`Packaged server accepted a connection on ${external.address}:${port}; it must listen on loopback only.`);
	}
	console.log(`Loopback-only check passed: ${external.address}:${port} is not reachable (${failure.code ?? failure.message}).`);
}

async function verifyVersionEndpoint(port: number, commit: string | undefined): Promise<void> {
	// `/version` is answered before the connection-token check, so it doubles as a liveness probe.
	const version = await request(port, '/version');
	if (version.status !== 200) {
		throw new Error(`Expected 200 from /version, received ${version.status}.`);
	}
	if (commit && version.body.trim() !== commit) {
		throw new Error(`/version returned "${version.body.trim()}", expected the packaged commit "${commit}".`);
	}
	console.log(`/version answered with the packaged commit ${version.body.trim() || '<empty>'}.`);
}

async function tokenCookie(port: number): Promise<string> {
	const tokenQuery = await request(port, `/?tkn=${encodeURIComponent(connectionToken)}`);
	if (tokenQuery.status !== 302) {
		throw new Error(`Expected 302 for a request carrying the connection token, received ${tokenQuery.status}.`);
	}
	const setCookie = tokenQuery.headers['set-cookie'];
	const cookie = (Array.isArray(setCookie) ? setCookie[0] : setCookie)?.split(';')[0];
	if (!cookie || !cookie.startsWith('vscode-tkn=')) {
		throw new Error(`Expected a vscode-tkn cookie for the connection token, received ${JSON.stringify(setCookie)}.`);
	}
	return cookie;
}

async function verifyTokenEnforcement(port: number): Promise<void> {
	const unauthenticated = await request(port, '/');
	if (unauthenticated.status !== 403) {
		throw new Error(`Expected 403 for an unauthenticated request, received ${unauthenticated.status}.`);
	}

	const wrongToken = await request(port, '/?tkn=not-the-token');
	if (wrongToken.status !== 403) {
		throw new Error(`Expected 403 for a wrong connection token, received ${wrongToken.status}.`);
	}

	await tokenCookie(port);
	console.log('Unauthenticated and wrongly authenticated requests rejected with 403; the token issued a cookie.');
}

async function verifyWebClient(port: number): Promise<void> {
	const cookie = await tokenCookie(port);

	const workbench = await request(port, '/', { cookie });
	if (workbench.status !== 200) {
		throw new Error(`Expected 200 for the authenticated workbench request, received ${workbench.status}.`);
	}
	for (const expected of ['Kodra', 'vscode-workbench-web-configuration', 'workbench.js']) {
		if (!workbench.body.includes(expected)) {
			throw new Error(`Served workbench HTML does not contain "${expected}".`);
		}
	}
	console.log('Authenticated workbench request served the Kodra workbench HTML.');

	for (const pattern of forbiddenOrigins) {
		if (pattern.test(workbench.body)) {
			throw new Error(`Served workbench HTML references a Microsoft-hosted origin matching ${pattern}.`);
		}
	}
	console.log('Served workbench HTML references no Microsoft-hosted origin.');

	const manifest = await request(port, '/static/resources/server/manifest.json', { cookie });
	if (manifest.status !== 200) {
		throw new Error(`Expected 200 for the web manifest, received ${manifest.status}.`);
	}
	const parsed = JSON.parse(manifest.body);
	if (typeof parsed.start_url !== 'string') {
		throw new Error(`Web manifest has no start_url: ${manifest.body}`);
	}
	console.log(`Web manifest served with start_url "${parsed.start_url}".`);

	const csp = workbench.headers['content-security-policy'];
	if (typeof csp !== 'string') {
		throw new Error('Served workbench HTML has no Content-Security-Policy header.');
	}
	console.log(`Workbench CSP: ${csp}`);
}

async function verifyNoWebClient(port: number): Promise<void> {
	const cookie = await tokenCookie(port);

	const root = await request(port, '/', { cookie });
	if (root.status !== 404) {
		throw new Error(`Expected 404 from the REH package root, received ${root.status}.`);
	}

	const workbench = await request(port, '/static/out/vs/code/browser/workbench/workbench.js', { cookie });
	if (workbench.status !== 404) {
		throw new Error(`Expected 404 for the browser workbench bundle, received ${workbench.status}.`);
	}
	console.log('REH package serves no browser workbench.');
}

async function stopProcess(child: ChildProcess): Promise<void> {
	if (child.exitCode !== null || child.signalCode !== null) {
		return;
	}

	const exited = new Promise<void>(resolve => child.once('exit', () => resolve()));
	child.kill();
	await Promise.race([exited, delay(5_000)]);
	if (child.exitCode === null && child.signalCode === null) {
		child.kill('SIGKILL');
		await exited;
	}
}

main(process.argv[2]).catch(error => {
	console.error(error);
	process.exit(1);
});
