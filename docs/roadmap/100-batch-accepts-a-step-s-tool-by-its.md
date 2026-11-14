---
order: 93
item: 100
status: Done
---

## Item

`batch` accepts a step's tool by its client name (`mcp__jrefactor__rename`, any server prefix)

## Status

Done (2026-11-05), local session: `Tools.named` strips `mcp__<server>__`, so the CLI takes it too; golden `batch_client_names`

## Evidence

A/B run: 6 batches in 4 of 16 mcp runs were refused as "No tool mcp__gorefactor__..." and resent
