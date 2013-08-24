package denominator.denominatord;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Iterator;

import javax.inject.Inject;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

class JsonCodec {

  private final Gson json;

  @Inject
  JsonCodec(Gson json) {
    this.json = json;
  }

  <T> T readJson(HttpServerExchange exchange, Class<T> clazz) throws Exception {
    exchange.startBlocking();
    InputStream in = exchange.getInputStream();
    try {
      return json.fromJson(new InputStreamReader(in), clazz);
    } finally {
      in.close();
    }
  }

  void toJsonArray(Iterator<?> elements, HttpServerExchange exchange) throws Exception {
    elements.hasNext(); // defensive to make certain error cases eager.
    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
    exchange.startBlocking();
    OutputStream out = exchange.getOutputStream();
    try {
      JsonWriter writer = new JsonWriter(new OutputStreamWriter(out));
      writer.setIndent("  ");
      writer.beginArray();
      int count = 0;
      while (elements.hasNext()) {
        Object next = elements.next();
        json.toJson(next, next.getClass(), writer);
        if (++count % 100 == 0) // better feedback on long lists
        {
          writer.flush();
        }
      }
      writer.endArray();
      writer.flush();
    } finally {
      out.write("\n".getBytes()); // curl nice
      out.close();
    }
  }
}
