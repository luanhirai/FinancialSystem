import test from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { pageTours, buildTourSteps } from "../src/lib/page-tours.mjs";

function pages(directory,route=""){
 return readdirSync(directory,{withFileTypes:true}).flatMap(entry=>{
  const file=join(directory,entry.name);
  if(entry.isDirectory())return pages(file,route+"/"+entry.name);
  if(!/^page\.(jsx?|tsx?)$/.test(entry.name))return [];
  const source=readFileSync(file,"utf8");
  return /import\s*{\s*redirect\s*}/.test(source)?[]:[route||"/"];
 });
}
test("all remaining pages have tutorials and login intentionally has none",()=>{
 for(const route of pages(fileURLToPath(new URL("../src/app",import.meta.url)))){
  if(route==="/login"){assert.equal(pageTours[route],undefined);assert.deepEqual(buildTourSteps(route,()=>null),[]);continue;}
  const tour=pageTours[route];
  assert.ok(tour,"Missing tutorial: "+route);
  assert.ok(tour.title&&tour.description&&tour.steps.length,"Incomplete tutorial: "+route);
 }
});
test("missing targets on empty or loading pages are skipped without losing introduction",()=>{
 const steps=buildTourSteps("/orders",()=>null);
 assert.equal(steps.length,2);
 assert.equal(steps[0].popover.title,"Pedidos");
 assert.equal(steps[1].popover.title,"Pronto!");
 assert.ok(steps.every(step=>!step.element));
});
test("resolved targets are preserved and a new tour never mutates shared configuration",()=>{
 const element={id:"fake-visible-filters"};
 const resolve=selector=>selector.includes("report-filters")?element:null;
 const steps=buildTourSteps("/reports/products",resolve);
 assert.equal(steps.length,3);
 assert.equal(steps[1].element,element);
 assert.equal(typeof pageTours["/reports/products"].steps[1].element,"string");
 assert.equal(buildTourSteps("/reports/channels",()=>null).length,2);
 assert.deepEqual(buildTourSteps("/not-a-page",resolve),[]);
});
