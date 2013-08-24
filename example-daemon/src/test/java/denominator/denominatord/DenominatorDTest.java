package denominator.denominatord;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import denominator.DNSApiManager;
import denominator.Denominator;
import denominator.mock.MockProvider;
import denominator.model.ResourceRecordSet;
import denominator.model.profile.Geo;
import denominator.model.rdata.CNAMEData;
import feign.Feign;
import feign.FeignException;
import feign.gson.GsonModule;

import static org.assertj.core.api.Assertions.assertThat;

public class DenominatorDTest {

  static DNSApiManager mock;
  static DenominatorDApi client;

  @BeforeClass
  public static void startServer() {
    DenominatorD.main("mock");
    String portOverride = System.getenv("DENOMINATORD_PORT");
    int port = portOverride != null ? Integer.parseInt(portOverride) : 8080;
    String url = "http://localhost:" + port;
    client = Feign.create(DenominatorDApi.class, url, new GsonModule());
    mock = Denominator.create(new MockProvider());
  }

  @Test
  public void healthcheckOK() {
    assertThat(client.healthcheck().status()).isEqualTo(200);
  }

  @Test
  public void zones() {
    assertThat(client.zones())
        .isNotEmpty()
        .containsAll(mock.api().zones());
  }

  @Test
  public void recordSets() {
    assertThat(client.recordSets("denominator.io.")).isNotEmpty();
  }

  @Test
  public void recordSetsWrongZoneIs400() {
    try {
      client.recordSets("moomoo.io.");
    } catch (FeignException e) {
      assertThat(e.getMessage()).startsWith("status 400");
    }
  }

  @Test
  public void recordSetsByName() {
    assertThat(client.recordSetsByName("denominator.io.", "www.denominator.io."))
        .isNotEmpty()
        .containsAll(toList(
            mock.api().recordSetsInZone("denominator.io.").iterateByName("www.denominator.io.")));
  }

  @Test
  public void recordSetsByNameWhenNotFound() {
    assertThat(client.recordSetsByName("denominator.io.", "moomoo.denominator.io.")).isEmpty();
  }

  @Test
  public void recordSetsByNameAndType() {
    assertThat(client.recordSetsByNameAndType("denominator.io.", "denominator.io.", "NS"))
        .isNotEmpty()
        .containsAll(toList(mock.api().recordSetsInZone("denominator.io.")
                                .iterateByNameAndType("denominator.io.", "NS")));
  }

  @Test
  public void recordSetsByNameAndTypeWhenNotFound() {
    assertThat(client.recordSetsByNameAndType("denominator.io.", "denominator.io.", "A")).isEmpty();
  }

  @Test
  public void recordSetsByNameTypeAndQualifier() {
    assertThat(client.recordsetsByNameAndTypeAndQualifier("denominator.io.",
                                                          "www.weighted.denominator.io.", "CNAME",
                                                          "EU-West"))
        .isNotEmpty()
        .containsOnly(mock.api().recordSetsInZone("denominator.io.")
                          .getByNameTypeAndQualifier("www.weighted.denominator.io.", "CNAME",
                                                     "EU-West"));
  }

  @Test
  public void recordSetsByNameTypeAndQualifierWhenNotFound() {
    assertThat(client.recordsetsByNameAndTypeAndQualifier("denominator.io.",
                                                          "www.weighted.denominator.io.", "CNAME",
                                                          "AU-West")).isEmpty();
  }

  @Test
  public void deleteRecordSetByNameAndType() {
    client.deleteRecordSetByNameAndType("denominator.io.", "www1.denominator.io.", "A");
    assertThat(client.recordSetsByNameAndType("denominator.io.", "www1.denominator.io.", "A"))
        .isEmpty();
  }

  @Test
  public void deleteRecordSetByNameAndTypeWhenNotFound() {
    client.deleteRecordSetByNameAndType("denominator.io.", "denominator.io.", "A");
  }

  @Test
  public void deleteRecordSetByNameTypeAndQualifier() {
    client.deleteRecordSetByNameTypeAndQualifier("denominator.io.", "www.weighted.denominator.io.",
                                                 "CNAME", "US-West");
    assertThat(client.recordsetsByNameAndTypeAndQualifier("denominator.io.",
                                                          "www.weighted.denominator.io.", "CNAME",
                                                          "US-West")).isEmpty();
  }

  @Test
  public void deleteRecordSetByNameTypeAndQualifierWhenNotFound() {
    client.deleteRecordSetByNameTypeAndQualifier("denominator.io.", "www.weighted.denominator.io.",
                                                 "CNAME", "AU-West");
  }

  @Test
  public void putRecordSet() {
    Map<String, Collection<String>> antarctica = new LinkedHashMap<String, Collection<String>>();
    antarctica.put("Antarctica",
                   Arrays.asList("Bouvet Island", "French Southern Territories", "Antarctica"));

    ResourceRecordSet<CNAMEData> recordSet = ResourceRecordSet.<CNAMEData>builder()
        .name("www.beta.denominator.io.")
        .type("CNAME")
        .qualifier("Antarctica")
        .ttl(300)
        .add(CNAMEData.create("www-south.denominator.io."))
        .geo(Geo.create(antarctica))
        .build();
    client.putRecordSet("denominator.io.", recordSet);
    assertThat(
        client.recordSetsByNameAndType("denominator.io.", recordSet.name(), recordSet.type()))
        .containsOnly(recordSet);
  }

  static List<ResourceRecordSet<?>> toList(Iterator<ResourceRecordSet<?>> iterator) {
    List<ResourceRecordSet<?>> inMock = new ArrayList<ResourceRecordSet<?>>();
    while (iterator.hasNext()) {
      inMock.add(iterator.next());
    }
    return inMock;
  }
}
