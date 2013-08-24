package denominator.denominatord;


import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import denominator.AllProfileResourceRecordSetApi;
import denominator.DNSApiManager;
import denominator.ReadOnlyResourceRecordSetApi;
import denominator.model.ResourceRecordSet;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;

import static denominator.common.Preconditions.checkArgument;
import static denominator.denominatord.DenominatorDRouter.DELETE_METHOD;
import static denominator.denominatord.DenominatorDRouter.GET_METHOD;
import static denominator.denominatord.DenominatorDRouter.PUT_METHOD;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

class RecordSetHandler implements HttpHandler {

  private static final org.jboss.logging.Logger
      log =
      org.jboss.logging.Logger.getLogger(RecordSetHandler.class);

  static final Pattern RECORDSET_PATTERN = Pattern.compile("/zones/([\\.\\w]+)/recordsets");

  private final DNSApiManager mgr;
  private final JsonCodec codec;

  @Inject
  RecordSetHandler(DNSApiManager mgr, JsonCodec toJson, JsonCodec codec) {
    this.mgr = mgr;
    this.codec = codec;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    Matcher matcher = RECORDSET_PATTERN.matcher(exchange.getRelativePath());
    if (!matcher.matches()) {
      ResponseCodeHandler.HANDLE_404.handleRequest(exchange);
      return;
    }
    String zoneIdOrName = matcher.group(1);
    AllProfileResourceRecordSetApi api = mgr.api().recordSetsInZone(zoneIdOrName);
    checkArgument(api != null, "cannot control record sets in zone %s", zoneIdOrName);
    if (GET_METHOD.equals(exchange.getRequestMethod())) {
      Query query = Query.from(exchange.getQueryParameters());
      codec.toJsonArray(recordSetsForQuery(api, query), exchange);
    } else if (PUT_METHOD.equals(exchange.getRequestMethod())) {
      ResourceRecordSet<?> recordSet = codec.readJson(exchange, ResourceRecordSet.class);
      Query query = Query.from(recordSet);
      long s = System.nanoTime();
      log.infof("replacing recordset %s ", query);
      api.put(recordSet);
      log.infof("replaced recordset %s in %sms", query,
                NANOSECONDS.toMillis(System.nanoTime() - s));
      exchange.setResponseCode(204);
    } else if (DELETE_METHOD.equals(exchange.getRequestMethod())) {
      Query query = Query.from(exchange.getQueryParameters());
      long s = System.nanoTime();
      log.infof("deleting recordset %s ", query);
      if (query.qualifier != null) {
        api.deleteByNameTypeAndQualifier(query.name, query.type, query.qualifier);
      } else if (query.type != null) {
        checkArgument(query.name != null, "name query required with type");
        api.deleteByNameAndType(query.name, query.type);
      } else if (query.name != null) {
        throw new IllegalArgumentException("you must specify both name and type when deleting");
      }
      log.infof("deleted recordset %s in %sms", query, NANOSECONDS.toMillis(System.nanoTime() - s));
      exchange.setResponseCode(204);
    }
  }

  static Iterator<?> recordSetsForQuery(ReadOnlyResourceRecordSetApi api, Query query) {
    if (query.qualifier != null) {
      ResourceRecordSet<?>
          recordSet =
          api.getByNameTypeAndQualifier(query.name, query.type, query.qualifier);
      return recordSet != null ? singleton(recordSet).iterator() : emptySet().iterator();
    } else if (query.type != null) {
      return api.iterateByNameAndType(query.name, query.type);
    } else if (query.name != null) {
      return api.iterateByName(query.name);
    }
    return api.iterator();
  }
}
