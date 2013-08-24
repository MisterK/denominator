package denominator.denominatord;

import java.util.Deque;
import java.util.Map;

import denominator.model.ResourceRecordSet;

import static denominator.common.Preconditions.checkArgument;
import static feign.Util.emptyToNull;

class Query {

  static Query from(Map<String, Deque<String>> queryParams) {
    return new Query(
        orNull(queryParams, "name"),
        orNull(queryParams, "type"),
        orNull(queryParams, "qualifier"));
  }

  static Query from(ResourceRecordSet<?> recordSet) {
    return new Query(recordSet.name(), recordSet.type(), recordSet.qualifier());
  }

  final String name;
  final String type;
  final String qualifier;

  private Query(String name, String type, String qualifier) {
    this.name = name;
    this.type = type;
    this.qualifier = qualifier;
    if (qualifier != null) {
      checkArgument(type != null && name != null, "name and type query required with qualifier");
    } else if (type != null) {
      checkArgument(name != null, "name query required with type");
    }
  }

  private static String orNull(Map<String, Deque<String>> params, String key) {
    return params.containsKey(key) ? emptyToNull(params.get(key).peekFirst()) : null;
  }

  @Override
  public String toString() {
    return new StringBuilder()
        .append("name=").append(name)
        .append(", type=").append(type)
        .append(", qualifier=").append(qualifier).toString();
  }
}
