# Upgrading Loomspan

Loomspan is in beta. Supported Java APIs and documented configuration keys and
behavior may change between beta releases, including breaking changes. Betas do
not guarantee source, binary, or behavioral compatibility with earlier betas.
The first production release will be `1.0.0`.

Before upgrading:

1. Record the application's current runtime version and choose an exact target.
   Review that target's release notes and migration information. If release notes
   are absent or incomplete, treat migration coverage as unknown.
2. For Sidecar, resolve its framework dependency from the exact Sidecar revision.
   For an SDK, check its owner's explicit compatibility facts independently.
3. Review changes to the supported Java API, configuration, and authored skills.
   The bundled [API compatibility reference](../agent-skills/loomspan-docs/references/java-api/compatibility-and-boundaries.md)
   and [connection migration guidance](../agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md)
   describe changes in their own revision; they are not a complete release-to-release ledger.
4. Update application dependencies and configuration deliberately, and validate
   representative skill invocations, authorization, outputs, and failure handling
   in your application environment.
5. Follow [loomspan-install](../agent-skills/loomspan-install/SKILL.md) to update
   assistant guidance for the selected runtime. Use the coordinated Console version
   and verify its connection. Guidance installation does not upgrade the runtime.

Do not treat a snapshot coordinate as immutable: pin the source revision used for
an evaluation when reproducibility matters. Source builds and artifact availability
for this checkout are covered in the [quickstart](quickstart-java.md).
