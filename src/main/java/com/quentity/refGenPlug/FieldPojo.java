package com.quentity.refGenPlug;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


import java.util.List;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FieldPojo {
  private String name;
  private String type;
  @Getter
  private List<String> generic;
}
