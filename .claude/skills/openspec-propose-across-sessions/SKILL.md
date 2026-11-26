---
name: openspec-propose-across-sessions
description: Propose one or more OpenSpec changes in parallel across several other Claude Code sessions (for example the backend, web and mobile sessions), with this session as the orchestrator. Each session writes its proposal in its own repo and sends its open questions back here; this session waits for every session's round, merges questions that need the same answer, and asks the user with AskUserQuestion, marking which session each decision is for. Planning only, no implementation.
disable-model-invocation: true
---

# OpenSpec propose across sessions

This session is the **orchestrator**. The other sessions are **peers**. Each
peer runs the OpenSpec propose workflow of its own repo. Whenever a peer would
ask the user something, it sends the question here instead. You collect the
questions of all peers, merge the ones that need the same answer, ask the user,
and send each peer its answers. This repeats until every peer has finished its
artifacts.

You never write artifacts in the peers' repos yourself, and nobody implements
anything. The skill ends when every peer has its planning artifacts.

## Tools

- `ListAgents` lists the peer sessions. Its first line names this session
  (`This session is <name> [ref]`). That name is the address peers reply to.
- `SendMessage` (deferred; load it with `ToolSearch` `select:SendMessage`)
  sends to a peer by name. Peers reply with `SendMessage` to your name. Their
  messages arrive as `<cross-session-message from="...">`.
- `AskUserQuestion` for every question to the user. Never ask the user in plain
  text what can be asked with it.

## Step 1: Work out the change or changes

The input is a description of what to propose, and optionally session names.

- **One change for all sessions** (for example "improve the test suite"): every
  peer gets the same description and adapts it to its repo.
- **Different changes per session** (for example "backend: add the endpoint,
  web and mobile: use it"): each peer gets its own description plus one line
  on what the other peers are proposing, so it can name the dependencies.

If it isn't clear which of the two the user means, or which part belongs to
which session, ask with AskUserQuestion before sending anything.

## Step 2: Find the sessions

Run `ListAgents`. Follow the `cursor` on the last line until you have every
page. Note this session's name from the header and never pick it as a peer.

**Names given.** Match each one against the listing: exact name first, then a
unique prefix. A name that matches exactly one session needs no confirmation.
A name that matches several or none goes into the confirmation question below,
with the closest candidates as options. If a session is not listed, say so.
Don't ask the user for an id or link.

**No names given.** Infer the candidates:

1. Take keywords from the change description (the change name in kebab-case,
   its main nouns) and from the products it touches (`backend`, `web`,
   `mobile`, `frontend`, `ios`, `android`, ...).
2. Rank sessions by how well their name matches those keywords. Use
   recency (`started ... ago`) to break ties: sessions started for this work
   are usually the newest. A group like `backend-x`, `web-x`, `mobile-x` with
   the same `x` is a strong signal.
3. Leave out generic names (`cardigan-3c`, `test-session`) unless nothing else
   matches.

**Confirm** the inferred set with one AskUserQuestion call, `multiSelect: true`,
one option per candidate session. Label: the session name. Description: its
state (`idle` / `busy` / `waiting`), when it started, and why it matched. Put
the best matches first. With more than four good candidates, split them over
two questions in the same call (for example by product). The user can add
other sessions through "Other".

A `busy` session gets messages only at its next tool round. A `waiting` session
is probably waiting for its own user. Mention both in the option description.

## Step 3: Set up the ledger

Keep the state of the run in a ledger file, so it survives context compaction:
in the scratchpad directory if the system prompt lists one, otherwise in a
temporary directory. Name it `propose-across-sessions-<change-name>.md`. Keep
in it, per peer: name, change description, status (`starting`, `asking`,
`answered`, `done`, `blocked`, `dropped`), repo path, change id, and every
question and answer by round. Update it after every message you send or get.
Read it again when you are unsure about the state.

## Step 4: Send the kickoff

Send each peer the kickoff below with `SendMessage`, in one response, so they
all start at once. Fill in the placeholders. The first line must stand alone,
because the peer's user sees only that line until they expand the message.

Also pass `notify_when_idle: true` on each kickoff. The idle notice tells you
when a peer stops working, even if it forgot to report.

```text
Orchestrated OpenSpec proposal: please propose "<change summary>" in this repo and route all questions to <orchestrator-name>.

You are one of several sessions proposing in parallel. The orchestrator
session <orchestrator-name> collects every question for the user from all
sessions, merges the ones that need the same answer, asks the user, and sends
the answers back to you.

What to propose here:
<change description for this peer>

What the other sessions propose (for context and dependencies only):
<one line per other peer: session name - its change>

How to work:
1. First reply to <orchestrator-name> with:
   ORCH ACK
   session: <your session name>
   repo: <absolute repo path>
   branch: <current branch>
   openspec: <yes / no OpenSpec root here>
   If there is no OpenSpec root, send ORCH BLOCKED instead and stop.
2. Run this repo's OpenSpec propose workflow for the change (/opsx:propose or
   the openspec-propose skill), with this repo's default schema and rules.
   Planning only: don't implement anything.
3. Never ask your user directly and never call AskUserQuestion. Wherever the
   workflow or a skill it calls (for example careful-planning) would ask the
   user, send the questions to <orchestrator-name> instead, in the
   ORCH QUESTIONS format below. Then end your turn and wait. The answers
   arrive as ORCH ANSWERS. Treat them exactly as if your user had chosen them.
4. Send everything that is ready to ask in one message per round: your whole
   frontier of open questions, not one question at a time. Leave out questions
   that depend on an unanswered one; they go in a later round.
5. Look up facts yourself (code, specs, shared specs, API contracts). Only
   send real decisions.
6. The final "is this the shared understanding?" confirmation also goes
   through the orchestrator, as a question with the summary in its context.
7. When all planning artifacts exist, send ORCH DONE. If you cannot go on,
   send ORCH BLOCKED with the reason. Don't message any session other than
   <orchestrator-name>.

ORCH QUESTIONS format:
ORCH QUESTIONS round=<n> session=<your session name> change=<change-id>
---
id: Q<n>
header: <topic, max 12 characters>
question: <one decision, ends with a question mark>
context: <what the user needs to know to decide, incl. facts you found>
multi: <yes/no>
options:
- <label> (Recommended) :: <consequence or trade-off>
- <label> :: <consequence or trade-off>
cross-repo: <yes if the answer must match in other repos (API contract,
  shared behavior, naming, shared specs), otherwise no>
---
(one block per question)

ORCH DONE format:
ORCH DONE session=<your session name> change=<change-id>
path: <change folder>
artifacts: <list>
summary: <3-5 lines: what the change does, dependencies on other repos>
```

## Step 5: Wait for the round

A round starts only when **every active peer** (status not `done`, `blocked` or
`dropped`) has reported: `ORCH QUESTIONS`, `ORCH DONE` or `ORCH BLOCKED`. This
is what makes merging possible. Don't ask the user about one peer's questions
while others are still working on theirs.

While waiting, end your turn with a short status line (who has reported, who
is still working). Incoming messages and idle notices start your next turn.
Don't poll `ListAgents` and don't send "are you done?" messages.

Handle these cases:

- **ACK** messages: record repo and branch. Several peers in the same repo and
  branch is a warning sign; tell the user.
- **Idle notice without a report:** the peer stopped without sending its round.
  Send one reminder that names what you expect, with `notify_when_idle: true`
  again. If it stops again without reporting, ask the user with AskUserQuestion:
  wait longer, continue without it this round, or drop it.
- **No reaction at all:** the peer may run in another permission mode and hold
  cross-session messages for its user's approval. Tell the user to approve the
  message in that session.
- **BLOCKED:** record it, tell the user the reason, and ask whether to fix it,
  drop the peer, or stop everything.
- **A message that isn't in the protocol** (a question in prose, a status
  update): treat a question as a question for the round, everything else as
  information.

## Step 6: Merge the questions

Take all questions of this round from all peers.

1. **Find the same decision.** Two questions are the same decision when one
   answer settles both: the same API field, contract, error code, naming,
   shared product behavior, or a choice the user would clearly make the same
   way everywhere (for example "Which coverage threshold?" asked by backend,
   web and mobile). Match by meaning, not wording. `cross-repo: yes` is a
   hint, not proof.
2. **Merge them into one question.** Start the question text with the session
   names in brackets, for example `[backend-tests, web-tests, mobile-tests]`.
   Combine the options and drop duplicates. Keep the most useful context from
   each peer. Put a recommended option first only if all peers recommend the
   same one; if they disagree, say so in the question text and leave out the
   marker.
3. **Similar but not the same.** If the questions look alike but the answer
   can reasonably differ per repo, don't merge. Ask them one after another in
   the same AskUserQuestion call so the user sees them side by side. If you
   are not sure, add one question that asks whether the answer should be the
   same for all sessions, and ask the rest in the next round.
4. **Session-specific questions** start with the session name in brackets,
   for example `[web-tests]`. Keep the peer's options and recommendation.
5. **Conflicts.** If a decision in one peer contradicts a question or a
   recommendation in another (for example backend proposes a field name that
   web asks about differently), point it out in the question text.

Never answer a question yourself and never drop one. Keep track of which
merged question maps to which original question ids.

## Step 7: Ask the user

- One AskUserQuestion call holds up to four questions. Use several calls in
  the same round when there are more.
- Order: merged cross-repo questions first, because they shape the rest. Then
  per-session questions, grouped by session.
- `header`: at most 12 characters, the topic. The session names go in the
  question text, not in the header.
- Keep the peers' `multi` setting. Don't add your own "Other" option.
- If the user answers through "Other", pass the text on unchanged.

## Step 8: Send the answers

Send each peer only its answers, in one message:

```text
ORCH ANSWERS round=<n> for <peer-name>: <k> answers, please continue.
Q1: <chosen label(s) or the user's own text>
Q2: <...> (decided once for: backend-tests, web-tests, mobile-tests)
Related decisions in other sessions:
- <only decisions from other peers that affect this peer, one line each>
```

Mark each peer `answered`, update the ledger, and go back to Step 5 for the
next round. Peers that sent `ORCH DONE` are out of the round.

## Step 9: Finish

When every peer is `done`, `blocked` or `dropped`:

1. Show a table: session, repo, change id, change folder, status.
2. List the dependencies between the changes, as the peers reported them
   (for example "web needs the backend endpoint first").
3. List the cross-repo decisions that were made once for several sessions.
4. Say which peers are blocked or dropped and why.

Stop there. Don't start implementation in any session. The user decides the
next step (for example reviewing the proposals, or `openspec-orchestrate` /
`/opsx:apply` in each repo).

## Rules

- Planning only. Never ask a peer to implement, commit, push, or do anything
  this session's permissions would block.
- Message only the peers the user confirmed.
- Only the user makes decisions. You merge and relay, you don't decide.
- Never put the user's personal details into a message beyond what the peers
  need for the change.
