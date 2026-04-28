## Dispatch

For each computational step in the Guide Manifest, use the Task tool to dispatch a sub-agent:

- **task:** "Read {guide-path} relative to the skill directory that dispatched you. Also read any shared guides listed. Input: {step-input-description}. Write your output to {output-path}."

Include in each task prompt:
- The flow/task name
- Camel version (from .camel-kit/config.properties)
- User answers relevant to this step
- File paths of prior step outputs (let the sub-agent read them)

### Fallback
If sub-agent dispatch is unavailable, read the guide directly into the main context and execute its instructions inline. This uses more tokens but produces equivalent results.
