package org.honton.chas.bom;

import com.fasterxml.jackson.jr.ob.JSON;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import lombok.Data;
import org.apache.maven.model.Dependency;

public class QueryCentral {

  private final HttpClient client =
      HttpClient.newBuilder()
          .version(Version.HTTP_1_1)
          .followRedirects(Redirect.NORMAL)
          .connectTimeout(Duration.ofSeconds(20))
          .build();

  Dependency getDependency(String sha1) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder()
            .GET()
            .uri(URI.create("https://search.maven.org/solrsearch/select?q=1:" + sha1))
            .build();

    HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

    String body = response.body();
    CentralWrapper wrapper = JSON.std.beanFrom(CentralWrapper.class, body);
    List<CentralDoc> docs = wrapper.response.docs;
    if (docs != null && !docs.isEmpty()) {
      CentralDoc doc = docs.get(0);
      Dependency dependency = new Dependency();
      dependency.setGroupId(doc.g);
      dependency.setArtifactId(doc.a);
      dependency.setVersion(doc.v);
      return dependency;
    }
    return null;
  }

  @Data
  public static class CentralDoc {
    private String g;
    private String a;
    private String v;
  }

  @Data
  public static class CentralResponse {
    private List<CentralDoc> docs;
  }

  @Data
  public static class CentralWrapper {
    private CentralResponse response;
  }
}
