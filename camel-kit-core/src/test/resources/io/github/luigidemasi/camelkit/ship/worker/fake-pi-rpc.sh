#!/bin/sh
fixture=$(dirname "$0")
if [ "${1:-}" = "--version" ]; then
  cat "$fixture/version"
  exit 0
fi

: > "$fixture/args"
session=""
session_dir=""
while [ "$#" -gt 0 ]; do
  printf '%s\n' "$1" >> "$fixture/args"
  if [ "$1" = "--session-id" ]; then
    shift
    session="$1"
    printf '%s\n' "$1" >> "$fixture/args"
  elif [ "$1" = "--session-dir" ]; then
    shift
    session_dir="$1"
    printf '%s\n' "$1" >> "$fixture/args"
  fi
  shift
done
pwd > "$fixture/cwd"
printf '%s\n' "$session_dir" >> "$fixture/session-dirs"
printf '%s\n' "$session" >> "$fixture/session-ids"

session_file=""
for existing in "$session_dir"/*_"$session".jsonl; do
  if [ -f "$existing" ]; then
    session_file="$existing"
    printf '%s\n' "$session" >> "$fixture/resumed-sessions"
    break
  fi
done
if [ -z "$session_file" ]; then
  session_file="$session_dir/2026-07-24T00-00-00-000Z_$session.jsonl"
fi

mode=$(cat "$fixture/mode")
if [ "$mode" = "never-read" ]; then
  sleep 300 &
  child=$!
  printf '%s\n' "$$" > "$fixture/parent-pid"
  printf '%s\n' "$child" > "$fixture/child-pid"
  trap 'kill "$child" 2>/dev/null; wait "$child" 2>/dev/null; exit 143' TERM HUP INT
  touch "$fixture/ready"
  wait "$child"
  exit 0
fi
if [ "$mode" = "duplicate-response-abort-after-blocked-prompt" ]; then
  printf '%s\n' '{"id":"prompt-1","type":"response","command":"prompt","success":true}'
  printf '%s\n' '{"id":"prompt-1","type":"response","command":"prompt","success":true}'
  touch "$fixture/ready"
  while [ ! -e "$fixture/release-prompt" ]; do sleep 0.01; done
fi

IFS= read -r prompt || exit 6
printf '%s\n' "$prompt" > "$fixture/prompt"
prompt_content=${prompt#'{"id":"prompt-1","type":"prompt","message":'}
prompt_content=${prompt_content%'}'}
user_message='{"role":"user","content":'"$prompt_content"',"timestamp":1}'
user_emitted=0
entry_count=0
last_id=""
current_user_id=""

initialize_state() {
  if [ -f "$session_file" ]; then
    entry_count=$(($(wc -l < "$session_file") - 1))
    last_id=$(sed -n 's/^{"type":"[^"]*","id":"\([^"]*\)".*/\1/p' "$session_file" | tail -n 1)
  fi
}

ensure_session() {
  if [ -f "$session_file" ]; then
    initialize_state
    return
  fi
  printf '{"type":"session","version":3,"id":"%s","timestamp":"2026-07-24T00:00:00.000Z","cwd":"%s"}\n' \
    "$session" "$PWD" > "$session_file"
  if [ "$mode" = "empty-session" ]; then
    : > "$session_file"
    return
  fi
  printf '%s\n' '{"type":"model_change","id":"e1","parentId":null,"timestamp":"2026-07-24T00:00:00.001Z","provider":"fixture","modelId":"fixture-model"}' >> "$session_file"
  printf '%s\n' '{"type":"thinking_level_change","id":"e2","parentId":"e1","timestamp":"2026-07-24T00:00:00.002Z","thinkingLevel":"off"}' >> "$session_file"
  entry_count=2
  last_id="e2"
}

append_message() {
  message="$1"
  role="$2"
  ensure_session
  if [ "$mode" = "empty-session" ] || [ "$mode" = "unchanged-session" ]; then
    return
  fi
  entry_count=$((entry_count + 1))
  id="e$entry_count"
  parent="$last_id"
  persisted="$message"
  printf '{"type":"message","id":"%s","parentId":"%s","timestamp":"2026-07-24T00:00:00.010Z","message":%s}\n' \
    "$id" "$parent" "$persisted" >> "$session_file"
  last_id="$id"
  if [ "$role" = "user" ]; then
    current_user_id="$id"
  fi
}

emit_message() {
  message="$1"
  role="$2"
  printf '{"type":"message_end","message":%s}\n' "$message"
  append_message "$message" "$role"
  if [ "$role" = "user" ]; then
    user_emitted=1
  fi
}

append_compaction() {
  ensure_session
  entry_count=$((entry_count + 1))
  id="e$entry_count"
  printf '{"type":"compaction","id":"%s","parentId":"%s","timestamp":"2026-07-24T00:00:00.020Z","summary":"summary","firstKeptEntryId":"%s","tokensBefore":10,"details":{"readFiles":[],"modifiedFiles":[]},"fromHook":false}\n' \
    "$id" "$last_id" "$current_user_id" >> "$session_file"
  last_id="$id"
}

begin_turn() {
  ensure_session
  if [ "$user_emitted" -eq 0 ]; then
    emit_message "$user_message" user
  fi
}

abort_turn() {
  IFS= read -r abort || exit 6
  printf '%s\n' "$abort" >> "$fixture/abort"
  if [ "$abort" != '{"id":"abort-1","type":"abort"}' ]; then
    exit 8
  fi
  if [ "$mode" = "repeated-interrupt-abort" ]; then
    touch "$fixture/abort-received"
    while [ ! -e "$fixture/release" ]; do sleep 0.01; done
  fi
  printf '%s\n' '{"id":"abort-1","type":"response","command":"abort","success":true}'
  begin_turn
  aborted='{"role":"assistant","content":[],"stopReason":"aborted","timestamp":2}'
  emit_message "$aborted" assistant
  printf '{"type":"agent_end","messages":[%s],"willRetry":false}\n' "$aborted"
  printf '%s\n' '{"type":"agent_settled"}'
  if [ "$mode" = "late-abort-exit" ]; then
    if IFS= read -r unexpected; then exit 9; fi
    touch "$fixture/ready"
    while [ ! -e "$fixture/release" ]; do sleep 0.01; done
    exit 0
  fi
  cat >/dev/null
}

if [ "$mode" = "duplicate-response-abort-after-blocked-prompt" ]; then
  abort_turn
  exit 0
fi

if [ "$mode" = "block" ]; then
  sleep 300 &
  child=$!
  printf '%s\n' "$$" > "$fixture/parent-pid"
  printf '%s\n' "$child" > "$fixture/child-pid"
  trap 'kill "$child" 2>/dev/null; wait "$child" 2>/dev/null; exit 143' TERM HUP INT
  printf '%s\n' '{"id":"prompt-1","type":"response","command":"prompt","success":true}'
  begin_turn
  touch "$fixture/ready"
  abort_turn
  kill "$child" 2>/dev/null
  wait "$child" 2>/dev/null
  trap - TERM HUP INT
  exit 0
fi
if [ "$mode" = "late-abort-exit" ] || [ "$mode" = "repeated-interrupt-abort" ]; then
  printf '%s\n' '{"id":"prompt-1","type":"response","command":"prompt","success":true}'
  begin_turn
  if [ "$mode" = "repeated-interrupt-abort" ]; then
    touch "$fixture/ready"
  fi
  abort_turn
  exit 0
fi
if [ "$mode" = "interrupt-output-limit" ] \
    || [ "$mode" = "interrupt-output-limit-natural-stop" ]; then
  printf '%s\n' '{"id":"prompt-1","type":"response","command":"prompt","success":true}'
  begin_turn
  touch "$fixture/ready"
  IFS= read -r abort || exit 6
  printf '%s\n' "$abort" > "$fixture/abort"
  if [ "$abort" != '{"id":"abort-1","type":"abort"}' ]; then
    exit 8
  fi
  dd if=/dev/zero bs=1048576 count=17 >&2 2>/dev/null
  printf '%s\n' '{"id":"abort-1","type":"response","command":"abort","success":true}'
  if [ "$mode" = "interrupt-output-limit-natural-stop" ]; then
    terminal='{"role":"assistant","content":[{"type":"text","text":"done"}],"stopReason":"stop","timestamp":2}'
  else
    terminal='{"role":"assistant","content":[],"stopReason":"aborted","timestamp":2}'
  fi
  emit_message "$terminal" assistant
  printf '{"type":"agent_end","messages":[%s],"willRetry":false}\n' "$terminal"
  printf '%s\n' '{"type":"agent_settled"}'
  cat >/dev/null
  exit 0
fi

case "$mode" in
  unacknowledged-abort)
    printf '%s\n' '{"id":"prompt-1","type":"response","command":"prompt","success":true}'
    IFS= read -r abort || exit 6
    printf '%s\n' "$abort" > "$fixture/abort"
    exit 0
    ;;
  malformed)
    printf 'not-json\n'
    abort_turn
    exit 0
    ;;
  invalid-utf)
    printf '\377\n'
    abort_turn
    exit 0
    ;;
  duplicate)
    printf '%s\n' '{"id":"prompt-1","type":"response","type":"response","command":"prompt","success":true}'
    abort_turn
    exit 0
    ;;
  response-wrong-bool)
    printf '%s\n' '{"id":"prompt-1","type":"response","command":"prompt","success":"true"}'
    abort_turn
    exit 0
    ;;
  response-wrong-id)
    printf '%s\n' '{"id":"other","type":"response","command":"prompt","success":true}'
    abort_turn
    exit 0
    ;;
  response-wrong-command)
    printf '%s\n' '{"id":"prompt-1","type":"response","command":"abort","success":true}'
    abort_turn
    exit 0
    ;;
  response-extra-field)
    printf '%s\n' '{"id":"prompt-1","type":"response","command":"prompt","success":true,"extra":true}'
    abort_turn
    exit 0
    ;;
  metadata-secret)
    printf '%s\n' '{"type":"fixture-secret-fragment"}'
    printf '%s\n' '{"id":"fixture-secret-fragment","type":"response","command":"fixture-secret-fragment","success":true}'
    abort_turn
    exit 0
    ;;
esac

if [ "$mode" = "nonzero" ]; then
  printf 'Pi failed\n' >&2
  exit 7
fi
if [ "$mode" = "oversized" ]; then
  printf '%s\n' '{"id":"prompt-1","type":"response","command":"prompt","success":true}'
  begin_turn
  dd if=/dev/zero bs=1048576 count=17 >&2 2>/dev/null
  abort_turn
  exit 0
fi
if [ "$mode" = "material-output-burst" ] \
    || [ "$mode" = "material-output-burst-trailing" ]; then
  printf '%s\n' '{"id":"prompt-1","type":"response","command":"prompt","success":true}'
  dd if=/dev/zero bs=1048576 count=1 2>/dev/null \
    | tr '\000' x > "$fixture/material-payload"
  burst=0
  while [ "$burst" -lt 17 ]; do
    printf '%s' '{"type":"material","payload":"'
    cat "$fixture/material-payload"
    printf '%s\n' '"}'
    burst=$((burst + 1))
  done
  rm -f "$fixture/material-payload"
  abort_turn
  if [ "$mode" = "material-output-burst-trailing" ]; then
    printf '%s' '{"type":"trailing","payload":"'
    dd if=/dev/zero bs=1048576 count=17 2>/dev/null | tr '\000' x
    printf '%s\n' '"}'
  fi
  exit 0
fi

if [ "$mode" = "preflight-hang" ]; then
  printf '%s\n' '{"type":"compaction_start","reason":"threshold"}'
  touch "$fixture/ready"
  sleep 300
  exit 0
fi

if [ "$mode" = "preflight-compaction-block" ]; then
  ensure_session
  current_user_id=$(sed -n 's/^{"type":"message","id":"\([^"]*\)".*"role":"user".*/\1/p' "$session_file" | tail -n 1)
  printf '%s\n' '{"type":"compaction_start","reason":"threshold"}'
  touch "$fixture/compaction-ready"
  while [ ! -e "$fixture/release-compaction" ]; do sleep 0.01; done
  append_compaction
  printf '%s\n' '{"type":"compaction_end","reason":"threshold","result":{"summary":"summary","firstKeptEntryId":"'"$current_user_id"'","tokensBefore":10,"estimatedTokensAfter":4,"details":{"readFiles":[],"modifiedFiles":[]}},"aborted":false,"willRetry":false}'
  printf '%s\n' '{"id":"prompt-1","type":"response","command":"prompt","success":true}'
  begin_turn
  IFS= read -r abort || exit 6
  printf '%s\n' "$abort" >> "$fixture/abort"
  aborted='{"role":"assistant","content":[],"stopReason":"aborted","timestamp":2}'
  emit_message "$aborted" assistant
  printf '{"type":"agent_end","messages":[%s],"willRetry":false}\n' "$aborted"
  printf '%s\n' '{"type":"agent_settled"}'
  printf '%s\n' '{"id":"abort-1","type":"response","command":"abort","success":true}'
  cat >/dev/null
  exit 0
fi

if [ "$mode" = "malformed-natural-stop-after-eof" ] \
    || [ "$mode" = "duplicate-response-natural-stop-after-eof" ]; then
  printf '%s\n' '{"id":"prompt-1","type":"response","command":"prompt","success":true}'
  begin_turn
  if [ "$mode" = "malformed-natural-stop-after-eof" ]; then
    printf '%s\n' 'not-json'
  else
    printf '%s\n' '{"id":"prompt-1","type":"response","command":"prompt","success":true}'
  fi
  cat > "$fixture/post-prompt-input"
  natural='{"role":"assistant","content":[{"type":"text","text":"done"}],"stopReason":"stop","timestamp":2}'
  emit_message "$natural" assistant
  printf '{"type":"agent_end","messages":[%s],"willRetry":false}\n' "$natural"
  printf '%s\n' '{"type":"agent_settled"}'
  touch "$fixture/ready"
  while [ ! -e "$fixture/release" ]; do sleep 0.01; done
  exit 0
fi

if [ "$mode" != "terminal-before-response" ]; then
  printf '%s\n' '{"id":"prompt-1","type":"response","command":"prompt","success":true}'
fi
if [ "$mode" = "pre-user-compaction" ]; then
  ensure_session
  current_user_id=$(sed -n 's/^{"type":"message","id":"\([^"]*\)".*"role":"user".*/\1/p' "$session_file" | tail -n 1)
  if [ -z "$current_user_id" ]; then
    current_user_id="e1"
  fi
  append_compaction
  printf '%s\n' '{"type":"compaction_end","reason":"threshold","result":{"summary":"summary","firstKeptEntryId":"'"$current_user_id"'","tokensBefore":10,"estimatedTokensAfter":4,"details":{"readFiles":[],"modifiedFiles":[]}},"aborted":false,"willRetry":false}'
fi
begin_turn

if [ "$mode" = "missing-content" ]; then
  printf '%s\n' '{"type":"agent_end","messages":[{"role":"assistant","stopReason":"stop","timestamp":2}],"willRetry":false}'
  abort_turn
  exit 0
fi
if [ "$mode" = "retry-wrong-bool" ]; then
  printf '%s\n' '{"type":"agent_end","messages":[{"role":"assistant","content":[],"stopReason":"stop","timestamp":2}],"willRetry":"false"}'
  abort_turn
  exit 0
fi

if [ "$mode" = "stop-error" ] || [ "$mode" = "stop-length" ]; then
  reason=${mode#stop-}
  semantic='{"role":"assistant","content":[],"stopReason":"'"$reason"'","timestamp":2}'
  emit_message "$semantic" assistant
  printf '{"type":"agent_end","messages":[%s],"willRetry":false}\n' "$semantic"
  printf '%s\n' '{"type":"agent_settled"}'
  exit 0
fi

if [ "$mode" = "retry-delay-block" ]; then
  retry='{"role":"assistant","content":[],"stopReason":"error","timestamp":2}'
  emit_message "$retry" assistant
  printf '{"type":"agent_end","messages":[%s],"willRetry":true}\n' "$retry"
  touch "$fixture/ready"
  IFS= read -r abort || exit 6
  printf '%s\n' "$abort" >> "$fixture/abort"
  if [ "$abort" != '{"id":"abort-1","type":"abort"}' ]; then
    exit 8
  fi
  printf '%s\n' '{"type":"agent_settled"}'
  printf '%s\n' '{"id":"abort-1","type":"response","command":"abort","success":true}'
  cat >/dev/null
  exit 0
fi

if [ "$mode" = "abort-hang" ]; then
  touch "$fixture/ready"
  IFS= read -r abort || exit 6
  printf '%s\n' "$abort" > "$fixture/abort"
  sleep 300
  exit 0
fi

if [ "$mode" = "natural-stop-after-abort" ]; then
  touch "$fixture/ready"
  IFS= read -r abort || exit 6
  printf '%s\n' "$abort" >> "$fixture/abort"
  natural='{"role":"assistant","content":[{"type":"text","text":"done"}],"stopReason":"stop","timestamp":2}'
  emit_message "$natural" assistant
  printf '{"type":"agent_end","messages":[%s],"willRetry":false}\n' "$natural"
  printf '%s\n' '{"type":"compaction_start","reason":"threshold"}'
  append_compaction
  printf '%s\n' '{"type":"compaction_end","reason":"threshold","result":{"summary":"summary","firstKeptEntryId":"'"$current_user_id"'","tokensBefore":10,"estimatedTokensAfter":4,"details":{"readFiles":[],"modifiedFiles":[]}},"aborted":false,"willRetry":false}'
  printf '%s\n' '{"type":"agent_settled"}'
  printf '%s\n' '{"id":"abort-1","type":"response","command":"abort","success":true}'
  cat >/dev/null
  exit 0
fi

if [ "$mode" = "overflow-compaction-abort" ]; then
  retry='{"role":"assistant","content":[],"stopReason":"error","timestamp":2}'
  emit_message "$retry" assistant
  printf '{"type":"agent_end","messages":[%s],"willRetry":false}\n' "$retry"
  printf '%s\n' '{"type":"compaction_start","reason":"overflow"}'
  touch "$fixture/compaction-ready"
  early_abort=$(timeout 0.5 sh -c 'IFS= read -r value && printf "%s" "$value"')
  if [ -n "$early_abort" ]; then
    printf '%s\n' "$early_abort" > "$fixture/abort"
    touch "$fixture/abort-received"
    touch "$fixture/premature-abort"
  fi
  while [ ! -e "$fixture/release-compaction" ]; do sleep 0.01; done
  append_compaction
  printf '%s\n' '{"type":"compaction_end","reason":"overflow","result":{"summary":"summary","firstKeptEntryId":"'"$current_user_id"'","tokensBefore":10,"estimatedTokensAfter":4,"details":{"readFiles":[],"modifiedFiles":[]}},"aborted":false,"willRetry":true}'
  touch "$fixture/compaction-ended"
  if [ -z "$early_abort" ]; then
    IFS= read -r abort || exit 6
    printf '%s\n' "$abort" > "$fixture/abort"
    touch "$fixture/abort-received"
  fi
  aborted='{"role":"assistant","content":[],"stopReason":"aborted","timestamp":3}'
  emit_message "$aborted" assistant
  printf '{"type":"agent_end","messages":[%s],"willRetry":false}\n' "$aborted"
  printf '%s\n' '{"type":"agent_settled"}'
  printf '%s\n' '{"id":"abort-1","type":"response","command":"abort","success":true}'
  cat >/dev/null
  exit 0
fi

if [ "$mode" = "partial-tool-abort" ]; then
  tools='{"role":"assistant","content":[{"type":"toolCall","id":"call-a","name":"read","arguments":{"path":"README.md"}},{"type":"toolCall","id":"call-b","name":"read","arguments":{"path":"pom.xml"}}],"stopReason":"toolUse","timestamp":2}'
  emit_message "$tools" assistant
  printf '%s\n' "call-a" >> "$fixture/tool-executions"
  result_a='{"role":"toolResult","toolCallId":"call-a","toolName":"read","content":[{"type":"text","text":"README"}],"details":{"path":"README.md"},"isError":false,"timestamp":3}'
  emit_message "$result_a" toolResult
  touch "$fixture/ready"
  IFS= read -r abort || exit 6
  printf '%s\n' "$abort" > "$fixture/abort"
  aborted='{"role":"assistant","content":[],"stopReason":"aborted","timestamp":4}'
  emit_message "$aborted" assistant
  printf '{"type":"agent_end","messages":[%s],"willRetry":false}\n' "$aborted"
  printf '%s\n' '{"type":"agent_settled"}'
  printf '%s\n' '{"id":"abort-1","type":"response","command":"abort","success":true}'
  cat >/dev/null
  exit 0
fi

if [ "$mode" = "overflow-continuation-tool-abort" ]; then
  retry='{"role":"assistant","content":[],"stopReason":"error","timestamp":2}'
  emit_message "$retry" assistant
  printf '{"type":"agent_end","messages":[%s],"willRetry":false}\n' "$retry"
  append_compaction
  printf '%s\n' '{"type":"compaction_end","reason":"overflow","result":{"summary":"summary","firstKeptEntryId":"'"$current_user_id"'","tokensBefore":10,"estimatedTokensAfter":4,"details":{"readFiles":[],"modifiedFiles":[]}},"aborted":false,"willRetry":true}'
  tools='{"role":"assistant","content":[{"type":"toolCall","id":"call-a","name":"read","arguments":{"path":"README.md"}},{"type":"toolCall","id":"call-b","name":"read","arguments":{"path":"pom.xml"}}],"stopReason":"toolUse","timestamp":3}'
  emit_message "$tools" assistant
  printf '%s\n' "call-a" >> "$fixture/tool-executions"
  result_a='{"role":"toolResult","toolCallId":"call-a","toolName":"read","content":[{"type":"text","text":"README"}],"details":{"path":"README.md"},"isError":false,"timestamp":4}'
  emit_message "$result_a" toolResult
  touch "$fixture/ready"
  IFS= read -r abort || exit 6
  printf '%s\n' "$abort" > "$fixture/abort"
  aborted='{"role":"assistant","content":[],"stopReason":"aborted","timestamp":5}'
  emit_message "$aborted" assistant
  printf '{"type":"agent_end","messages":[%s],"willRetry":false}\n' "$aborted"
  printf '%s\n' '{"type":"agent_settled"}'
  printf '%s\n' '{"id":"abort-1","type":"response","command":"abort","success":true}'
  cat >/dev/null
  exit 0
fi

printf '%s\n' 'Authorization=Bearer fixture-secret' >&2
assistant_text=$(cat "$fixture/assistant-text")
if [ "$mode" = "leak-assistant" ]; then
  assistant_text=$(cat "$fixture/secret")
fi
final='{"role":"assistant","content":[{"type":"text","text":"'"$assistant_text"'"}],"stopReason":"stop","timestamp":2}'
if [ "$mode" = "leak-assistant-key" ]; then
  secret=$(cat "$fixture/secret")
  final='{"role":"assistant","content":[{"type":"text","text":"done","details":{"'"$secret"'":true}}],"stopReason":"stop","timestamp":2}'
fi

if [ "$mode" = "cumulative-update-burst" ] \
    || [ "$mode" = "cumulative-tool-update-burst" ]; then
  partial=""
  update=0
  while [ "$update" -lt 2050 ]; do
    partial="${partial}xxxx"
    if [ "$mode" = "cumulative-update-burst" ]; then
      printf '{"type":"message_update","assistantMessageEvent":{"type":"text_delta","delta":"xxxx","partial":{"role":"assistant","content":[{"type":"text","text":"%s"}]}},"message":{"role":"assistant","content":[{"type":"text","text":"%s"}]}}\n' \
        "$partial" "$partial"
    else
      printf '{"type":"tool_execution_update","toolCallId":"call-1","toolName":"bash","args":{"command":"generate"},"partialResult":{"content":[{"type":"text","text":"%s"}],"details":{"output":"%s"}}}\n' \
        "$partial" "$partial"
    fi
    update=$((update + 1))
  done
fi

if [ "$mode" = "retry-then-success" ] \
    || [ "$mode" = "retry-length-then-success" ] \
    || [ "$mode" = "auto-retry-success" ]; then
  retry_reason="error"
  if [ "$mode" = "retry-length-then-success" ]; then
    retry_reason="length"
  fi
  retry='{"role":"assistant","content":[],"stopReason":"'"$retry_reason"'","timestamp":2}'
  emit_message "$retry" assistant
  if [ "$mode" = "retry-then-success" ] \
      || [ "$mode" = "retry-length-then-success" ]; then
    printf '{"type":"agent_end","messages":[%s],"willRetry":false}\n' "$retry"
    printf '%s\n' '{"type":"compaction_start","reason":"overflow"}'
    append_compaction
    printf '%s\n' '{"type":"compaction_end","reason":"overflow","result":{"summary":"summary","firstKeptEntryId":"'"$current_user_id"'","tokensBefore":10,"estimatedTokensAfter":4,"details":{"readFiles":[],"modifiedFiles":[]}},"aborted":false,"willRetry":true}'
  else
    printf '{"type":"agent_end","messages":[%s],"willRetry":true}\n' "$retry"
    printf '%s\n' '{"type":"auto_retry_start","attempt":1,"maxAttempts":3,"delayMs":1,"error":"retry"}'
  fi
elif [ "$mode" = "tool-sequence" ] \
    || [ "$mode" = "leak-tool" ] \
    || [ "$mode" = "leak-tool-number" ] \
    || [ "$mode" = "tool-orphan-stop" ]; then
  if [ "$mode" = "tool-orphan-stop" ]; then
    tool_assistant='{"role":"assistant","content":[{"type":"toolCall","id":"call-1","name":"read","arguments":{"path":"README.md"}},{"type":"toolCall","id":"call-2","name":"read","arguments":{"path":"pom.xml"}}],"stopReason":"toolUse","timestamp":2}'
  else
    tool_assistant='{"role":"assistant","content":[{"type":"toolCall","id":"call-1","name":"read","arguments":{"path":"README.md"}}],"stopReason":"toolUse","timestamp":2}'
  fi
  emit_message "$tool_assistant" assistant
  tool_text="tool output"
  if [ "$mode" = "leak-tool" ]; then
    tool_text=$(cat "$fixture/secret")
  fi
  tool_result='{"role":"toolResult","toolCallId":"call-1","toolName":"read","content":[{"type":"text","text":"'"$tool_text"'"}],"details":{"path":"README.md"},"isError":false,"timestamp":3}'
  if [ "$mode" = "leak-tool-number" ]; then
    secret=$(cat "$fixture/secret")
    tool_result='{"role":"toolResult","toolCallId":"call-1","toolName":"read","content":[{"type":"text","text":"tool output"}],"details":{"nested":{"value":'"$secret"'}},"isError":false,"timestamp":3}'
  fi
  emit_message "$tool_result" toolResult
elif [ "$mode" = "unrelated-intermediate" ]; then
  unrelated='{"role":"assistant","content":[],"stopReason":"stop","timestamp":2}'
  emit_message "$unrelated" assistant
fi

emit_message "$final" assistant
printf '{"type":"agent_end","messages":[%s],"willRetry":false}\n' "$final"

if [ "$mode" = "compaction-success" ] || [ "$mode" = "compact-after-stop" ]; then
  printf '%s\n' '{"type":"compaction_start","reason":"threshold"}'
  append_compaction
  printf '%s\n' '{"type":"compaction_end","reason":"threshold","result":{"summary":"summary","firstKeptEntryId":"'"$current_user_id"'","tokensBefore":10,"estimatedTokensAfter":4,"details":{"readFiles":[],"modifiedFiles":[]}},"aborted":false,"willRetry":false}'
elif [ "$mode" = "compaction-failed" ]; then
  printf '%s\n' '{"type":"compaction_start","reason":"threshold"}'
  printf '%s\n' '{"type":"compaction_end","reason":"threshold","aborted":false,"willRetry":false,"errorMessage":"compaction failed"}'
elif [ "$mode" = "compaction-aborted" ]; then
  printf '%s\n' '{"type":"compaction_start","reason":"threshold"}'
  printf '%s\n' '{"type":"compaction_end","reason":"threshold","aborted":true,"willRetry":false}'
fi

if [ "$mode" = "missing-settled" ]; then
  exit 0
elif [ "$mode" = "settled-extra" ]; then
  printf '%s\n' '{"type":"agent_settled","extra":true}'
else
  printf '%s\n' '{"type":"agent_settled"}'
fi

if [ "$mode" = "terminal-before-response" ]; then
  printf '%s\n' '{"id":"prompt-1","type":"response","command":"prompt","success":true}'
fi
if [ "$mode" = "trailing" ]; then
  printf '%s\n' '{"type":"message_update"}'
fi
if [ "$mode" = "session-duplicate" ]; then
  cp "$session_file" "$session_dir/2026-07-24T00-00-01-000Z_$session.jsonl"
fi
if [ "$mode" = "settled-nonzero" ]; then
  exit 7
fi
if [ "$mode" = "retention-failure" ]; then
  chmod 500 "$(dirname "$PWD")/evidence"
  exit 0
fi
if [ "$mode" = "settled-hang" ]; then
  trap '' TERM HUP INT
  sleep 300
fi
if [ "$mode" = "late-terminal-exit" ]; then
  if IFS= read -r unexpected; then exit 9; fi
  touch "$fixture/ready"
  while [ ! -e "$fixture/release" ]; do sleep 0.01; done
  exit 0
fi
if [ "$mode" = "fast-detach" ]; then
  (
    trap '' TERM HUP INT
    sleep 1
    touch "$fixture/delayed-mutation"
  ) >/dev/null 2>&1 &
  printf '%s\n' "$!" > "$fixture/detached-pid"
  exit 0
fi
if [ "$mode" = "publication-failure" ]; then
  publication_target="$(dirname "$session_dir")/$(basename "$session_file")"
  mkdir "$publication_target"
  touch "$publication_target/block"
  printf '%s\n' "$publication_target" > "$fixture/publication-target"
fi
cat >/dev/null
