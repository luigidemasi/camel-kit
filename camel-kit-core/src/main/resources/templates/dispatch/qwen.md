## Dispatch

For each computational step in the Guide Manifest, delegate to the corresponding pre-registered sub-agent by name.

Example:
- "Have the camel-component-selector sub-agent select components. Input: {step-input-description}. Write output to {output-path}."
- "Have the camel-tdd-assembler sub-agent assemble the TDD. Input: all .steps/ outputs. Write output to {final-tdd-path}."

Include in each delegation:
- The flow/task name
- Camel version (from .camel-kit/config.properties)
- User answers relevant to this step
- File paths of prior step outputs (let the sub-agent read them)

### Fallback
If the named sub-agent is not available, read the guide directly into the main context and execute its instructions inline. This uses more tokens but produces equivalent results.
