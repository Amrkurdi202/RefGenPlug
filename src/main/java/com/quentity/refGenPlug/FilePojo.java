package com.quentity.refGenPlug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import lombok.*;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FilePojo {
  @Singular
  @Getter
  private Set<EntityPojo> entities;
  public static final Gson GSON = new GsonBuilder()
          .registerTypeAdapter(FilePojo.class, (InstanceCreator<FilePojo>) type -> new FilePojo())
          .registerTypeAdapter(EntityPojo.class, (InstanceCreator<EntityPojo>) type -> new EntityPojo())
          .registerTypeAdapter(FieldPojo.class, (InstanceCreator<FieldPojo>) type -> new FieldPojo())
          .registerTypeAdapter(DiePojo.class, (InstanceCreator<DiePojo>) type -> new DiePojo())
          .create();

  public void write(File reflectionFile) throws IOException {
    FileWriter fileWriter = new FileWriter(reflectionFile);
    fileWriter.write(GSON.toJson(this));
    fileWriter.flush();
    fileWriter.close();
  }

  public static FilePojo read(File reflectionFile) throws IOException {
    FileReader fileReader = new FileReader(reflectionFile);
    FilePojo filePojo = GSON.fromJson(fileReader, FilePojo.class);
    fileReader.close();
    return filePojo;
  }
}
