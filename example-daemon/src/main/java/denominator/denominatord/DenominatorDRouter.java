package denominator.denominatord;

import javax.inject.Inject;

import denominator.DNSApiManager;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.AllowedMethodsHandler;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

import static denominator.denominatord.RecordSetHandler.RECORDSET_PATTERN;

class DenominatorDRouter implements HttpHandler {

  static final HttpString GET_METHOD = HttpString.tryFromString("GET");
  static final HttpString PUT_METHOD = HttpString.tryFromString("PUT");
  static final HttpString DELETE_METHOD = HttpString.tryFromString("DELETE");

  private final HttpHandler healthcheck;
  private final HttpHandler zones;
  private final HttpHandler recordSets;

  @Inject
  DenominatorDRouter(HealthCheck healthCheck, Zones zones, RecordSetHandler recordSets) {
    this.healthcheck = new AllowedMethodsHandler(healthCheck, GET_METHOD);
    this.zones = new AllowedMethodsHandler(zones, GET_METHOD);
    this.recordSets = new AllowedMethodsHandler(recordSets, GET_METHOD, PUT_METHOD, DELETE_METHOD);
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    try {
      if ("/healthcheck".equals(exchange.getRelativePath())) {
        healthcheck.handleRequest(exchange);
      } else if ("/zones".equals(exchange.getRelativePath())) {
        zones.handleRequest(exchange);
      } else if (RECORDSET_PATTERN.matcher(exchange.getRelativePath()).matches()) {
        recordSets.handleRequest(exchange);
      } else {
        exchange.setResponseCode(404);
      }
    } catch (RuntimeException e) {
      if (!exchange.isResponseStarted()) {
        exchange.setResponseCode(e instanceof IllegalArgumentException ? 400 : 500);
      }
      exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
      exchange.getResponseSender().send(e.getMessage() + "\n"); // curl nice
    }
  }

  static class HealthCheck implements HttpHandler {

    private final DNSApiManager mgr;

    @Inject
    HealthCheck(DNSApiManager mgr) {
      this.mgr = mgr;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      exchange.setResponseCode(mgr.checkConnection() ? 200 : 503);
    }
  }

  static class Zones implements HttpHandler {

    private final DNSApiManager mgr;
    private final JsonCodec codec;

    @Inject
    Zones(DNSApiManager mgr, JsonCodec codec) {
      this.mgr = mgr;
      this.codec = codec;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
      codec.toJsonArray(mgr.api().zones().iterator(), exchange);
    }
  }
}
