package ai.loomspan.internal.runtime.planning;

import ai.loomspan.internal.schema.CanonicalSchema;
import ai.loomspan.internal.schema.StandardSchemaCompiler;

/** Framework-owned initial plan generation contract; runtime identity is excluded. */
final class PlanningOutputContract {
    private PlanningOutputContract() {}
    static final CanonicalSchema SCHEMA = new StandardSchemaCompiler().compile("""
        {
          "type":"object", "additionalProperties":false,
          "required":["capabilityName","createdAt","status","tasks"],
          "properties":{
            "capabilityName":{"type":"string","minLength":1},
            "createdAt":{"type":"string","format":"date-time"},
            "status":{"type":["string","null"],"enum":["VALID",null]},
            "tasks":{"type":"array","items":{
              "type":"object","additionalProperties":false,
              "required":["taskId","title","status","capabilityName","intent","dependsOn","expectedOutputs","parallelGroup","note"],
              "properties":{
                "taskId":{"type":"string","minLength":1},
                "title":{"type":"string","minLength":1},
                "status":{"type":"string","enum":["PENDING"]},
                "capabilityName":{"type":"string","minLength":1},
                "intent":{"type":["string","null"]},
                "dependsOn":{"type":"array","items":{"type":"string"}},
                "expectedOutputs":{"type":["array","null"],"items":{"type":"string"}},
                "parallelGroup":{"type":["string","null"]},
                "note":{"type":["string","null"]}
              }
            }}
          }
        }
        """, StandardSchemaCompiler.Role.PLANNING, StandardSchemaCompiler.Origin.GENERATED);
}
