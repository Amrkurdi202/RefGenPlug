package com.quentity.refGenPlug;

import lombok.*;

import java.util.Set;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EntityPojo {
  private String name;
  @Singular
  private Set<FieldPojo> fields;
  @Singular
  @Setter
  private Set<DiePojo> dies;

}
