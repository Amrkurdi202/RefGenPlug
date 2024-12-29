package com.quentity.refGenPlug;

import com.google.gson.Gson;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

@Builder
@Getter
public class FilePojo {
  @Singular
  @Getter
  private final Set<EntityPojo> entities;

  public void write(File reflectionFile) throws IOException {
    FileWriter fileWriter = new FileWriter(reflectionFile);
    fileWriter.write(new Gson().toJson(this));
    fileWriter.flush();
    fileWriter.close();
  }

  public static FilePojo read(File reflectionFile) throws IOException {
    FileReader fileReader = new FileReader(reflectionFile);
    FilePojo filePojo = new Gson().fromJson(fileReader, FilePojo.class);
    fileReader.close();
    return filePojo;
  }
}
