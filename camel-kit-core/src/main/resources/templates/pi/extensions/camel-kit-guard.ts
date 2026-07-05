import fs from "node:fs";
import path from "node:path";

type GuardRule = {
  id: string;
  toolNames?: string[];
  inputPattern: string;
  flags?: string;
  reason: string;
};

type GuardPolicy = {
  version: number;
  rules: GuardRule[];
};

function policyPath(): string {
  let current = process.cwd();
  while (true) {
    const candidate = path.join(current, ".pi", "camel-kit-guard-policy.json");
    if (fs.existsSync(candidate)) {
      return candidate;
    }
    const parent = path.dirname(current);
    if (parent === current) {
      return path.join(process.cwd(), ".pi", "camel-kit-guard-policy.json");
    }
    current = parent;
  }
}

function loadPolicy(): GuardPolicy {
  const raw = fs.readFileSync(policyPath(), "utf8");
  const parsed = JSON.parse(raw) as GuardPolicy;
  if (parsed.version !== 1 || !Array.isArray(parsed.rules)) {
    throw new Error("Unsupported Camel Kit guard policy format");
  }
  return parsed;
}

function inputText(input: unknown): string {
  if (typeof input === "string") {
    return input;
  }
  if (input && typeof input === "object") {
    const value = input as Record<string, unknown>;
    for (const key of ["command", "cmd", "path", "file", "content"]) {
      if (typeof value[key] === "string") {
        return value[key] as string;
      }
    }
  }
  return JSON.stringify(input ?? "");
}

function appliesTo(rule: GuardRule, toolName: string): boolean {
  return !rule.toolNames || rule.toolNames.length === 0 || rule.toolNames.includes(toolName);
}

export default function camelKitGuard(pi: any) {
  const policy = loadPolicy();

  pi.on("tool_call", (event: any) => {
    const toolName = String(event.toolName ?? event.name ?? "");
    const text = inputText(event.input ?? event.args ?? event);

    for (const rule of policy.rules) {
      if (!appliesTo(rule, toolName)) {
        continue;
      }
      const pattern = new RegExp(rule.inputPattern, rule.flags ?? "i");
      if (pattern.test(text)) {
        return {
          block: true,
          reason: rule.reason
        };
      }
    }
    return undefined;
  });
}
