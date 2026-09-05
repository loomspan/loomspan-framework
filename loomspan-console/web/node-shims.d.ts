declare module "node:path" {
  const path: {
    dirname(value: string): string;
    join(...values: string[]): string;
    resolve(...values: string[]): string;
  };
  export default path;
}

declare module "node:url" {
  export function fileURLToPath(value: string | URL): string;
}

declare module "node:fs" {
  const fs: {
    readFileSync(path: string, encoding: "utf8"): string;
    readFileSync(path: string): Buffer;
    mkdtempSync(prefix: string): string;
    rmSync(path: string, options: { recursive: boolean; force: boolean }): void;
  };
  export default fs;
}

declare module "node:os" {
  const os: { tmpdir(): string };
  export default os;
}

declare module "node:child_process" {
  import type { EventEmitter } from "node:events";
  export type ChildProcessWithoutNullStreams = EventEmitter & {
    stdout: EventEmitter;
    stderr: EventEmitter;
    exitCode: number | null;
    kill(): boolean;
  };
  export function spawn(
    command: string,
    args: string[],
    options: { stdio: string[]; windowsHide: boolean },
  ): ChildProcessWithoutNullStreams;
}

declare module "node:http" {
  type IncomingMessage = {
    method?: string;
    url?: string;
    headers: Record<string, string | string[] | undefined>;
    on(event: string, listener: (...args: any[]) => void): IncomingMessage;
  };
  type ServerResponse = {
    writeHead(status: number, headers?: Record<string, string>): ServerResponse;
    write(body: string | Uint8Array): boolean;
    end(body?: string | Uint8Array): void;
  };
  type AddressInfo = { port: number };
  type Server = {
    listen(port: number, host: string, callback: () => void): void;
    address(): AddressInfo | string | null;
    close(callback: (error?: Error) => void): void;
    closeAllConnections(): void;
    closeIdleConnections(): void;
  };
  const http: {
    createServer(
      listener: (request: IncomingMessage, response: ServerResponse) => void,
    ): Server;
  };
  export default http;
}

declare module "node:events" {
  export class EventEmitter {
    on(event: string, listener: (...args: any[]) => void): this;
    once(event: string, listener: (...args: any[]) => void): this;
  }
}

declare class Buffer extends Uint8Array {
  static isBuffer(value: unknown): value is Buffer;
  static from(value: Uint8Array | string, encoding?: string): Buffer;
  static concat(list: Uint8Array[]): Buffer;
  readonly length: number;
  equals(other: Uint8Array): boolean;
  toString(encoding?: string): string;
}

declare const process: {
  env: Record<string, string | undefined>;
  platform: string;
};

interface ImportMeta {
  readonly url: string;
}
