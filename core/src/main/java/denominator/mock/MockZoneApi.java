package denominator.mock;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListSet;

import denominator.model.ResourceRecordSet;
import denominator.model.Zone;
import denominator.model.rdata.SOAData;

import static denominator.common.Preconditions.checkArgument;
import static denominator.model.ResourceRecordSets.ns;

final class MockZoneApi implements denominator.ZoneApi {

  private static final Comparator<ResourceRecordSet<?>> TO_STRING =
      new Comparator<ResourceRecordSet<?>>() {
        @Override
        public int compare(ResourceRecordSet<?> arg0, ResourceRecordSet<?> arg1) {
          return arg0.toString().compareTo(arg1.toString());
        }
      };

  private final Map<String, Collection<ResourceRecordSet<?>>> data;

  MockZoneApi(Map<String, Collection<ResourceRecordSet<?>>> data) {
    this.data = data;
    create("denominator.io.");
  }

  public void create(String idOrName) {
    checkArgument(!data.containsKey(idOrName), "zone %s already exists", idOrName);
    Collection<ResourceRecordSet<?>>
        zone =
        new ConcurrentSkipListSet<ResourceRecordSet<?>>(TO_STRING);
    zone.add(ResourceRecordSet.builder()
                 .type("SOA")
                 .name(idOrName)
                 .ttl(3600)
                 .add(SOAData.builder().mname("ns1." + idOrName).rname("admin." + idOrName)
                          .serial(1).refresh(3600).retry(600).expire(604800).minimum(60).build())
                 .build());
    zone.add(ns(idOrName, 86400, "ns1." + idOrName));
    data.put(idOrName, zone);
  }

  @Override
  public Iterator<Zone> iterator() {
    final Iterator<String> delegate = data.keySet().iterator();
    return new Iterator<Zone>() {
      @Override
      public boolean hasNext() {
        return delegate.hasNext();
      }

      @Override
      public Zone next() {
        return Zone.create(delegate.next());
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException("remove");
      }
    };
  }
}
