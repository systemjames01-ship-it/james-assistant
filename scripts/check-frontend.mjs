import { readFileSync } from "node:fs";
import vm from "node:vm";

const html = readFileSync(new URL("../index.html", import.meta.url), "utf8");
const scripts = [...html.matchAll(/<script\\b[^>]*>([\\s\\S]*?)<\\/script>/gi)];
if (scripts.length === 0) {
  throw new Error("No inline JavaScript block found in index.html");
}
for (let i = 0; i < scripts.length; i++) {
  new vm.Script(scripts[i][1], { filename: `index.html:inline-script-${i + 1}` });
}
console.log(`Validated ${scripts.length} inline script block(s) in index.html`);
