package com.quentity.refGenPlug;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Singular;
import java.util.Set;

@Builder
@Getter
public class EntityPojo {
  private String name;
  @Singular
  private final Set<FieldPojo> fields;
  @Singular
  @Setter
  private Set<DiePojo> dies;

}
