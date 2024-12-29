package com.quentity.refGenPlug;

import lombok.Builder;
import lombok.Getter;


import java.util.List;

@Builder
@Getter
public class FieldPojo {
  private String name;
  private String type;
  @Getter
  private final List<String> generic;
}
