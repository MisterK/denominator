# DenominatorD Example

DenominatorD is an example HTTP server that exports your DNS account as a REST api.  Technically, it is an [Undertow](https://github.com/undertow-io/undertow) server woven with [Dagger](https://github.com/square/dagger).  Once built, denominatord is a [really executable jar](http://skife.org/java/unix/2011/06/20/really_executable_jars.html), weighing in at <3MB, and starting up in 200-250ms on a modern laptop.

## Building
To build the daemon, execute `gradle clean build`.  The binary will end up at `./build/denominatord`.  If you don't have gradle, install it.

## Running
The syntax is simple.  First arg is the name of the provider.  For example, clouddns, designate, dynect, mock, route53, or ultradns.  The remaining args are any credentials to that provider.

Ex. If you have no account, you can use mock.

```bash
$ build/denominatord mock
targeting MockProvider{name=mock,url=mem:mock}
Aug 24, 2013 2:57:07 PM org.xnio.Xnio <clinit>
INFO: XNIO Version 3.1.0.CR6
Aug 24, 2013 2:57:07 PM org.xnio.nio.NioXnio <clinit>
INFO: XNIO NIO Implementation Version 3.1.0.CR6
listening on http://localhost:8080
initialized in 200ms
```

Ex. To connect to a real cloud, you'll specify your credentials.  You'll notice status messages for each outbound request.

```bash
$ build/denominatord route53 accessKey secretKey
targeting Route53Provider{name=route53,url=https://route53.amazonaws.com}
Aug 24, 2013 2:55:44 PM org.xnio.Xnio <clinit>
INFO: XNIO Version 3.1.0.CR6
Aug 24, 2013 2:55:44 PM org.xnio.nio.NioXnio <clinit>
INFO: XNIO NIO Implementation Version 3.1.0.CR6
listening on http://localhost:8080
initialized in 251ms
[Route53#listHostedZones] ---> GET https://route53.amazonaws.com/2012-12-12/hostedzone HTTP/1.1
[Route53#listHostedZones] <--- HTTP/1.1 200 OK (734ms)
```

By default, denominatord listens on port 8080.  Export `DENOMINATORD_PORT` to use a different port.

## API
The api is read-only, and based on [OpenStack Designate V2](https://wiki.openstack.org/wiki/Designate/APIv2).

Output is always json, and there's really only a few error cases.
  * 404 for an invalid request.
  * 400 for a valid request, but bad data.
  * 500 when the server blows up.

Here are the resources exposed.

### HealthCheck

#### GET /healthcheck
Returns 200 when the dns provider is healthy, 503 if not.

Ex. you might want to put a guard in a shell script to fail when health is bad.
```bash
$ curl -f http://localhost:8080/healthcheck
```

### Zones

#### GET /zones
Returns a possibly empty array of your zones.

Ex. for clouds like ultradns, you'll only see the zone name.
```bash
$ curl http://localhost:8080/zones
[
  {
    "name": "denominator.io."
  }
]
```

Ex. for clouds like route53, zones are not unique by name, so you'll see an `id`.

```bash
$ curl http://localhost:8080/zones
[
  {
    "name": "myzone.com.",
    "id": "ABCDEFGHIJK"
  }
]
```

### Record Sets
All record set commands require the zone specified as a path parameter.  This is either the id of the zone,
or when there is no id, it is the name.

```
/zones/{zoneIdOrName}/recordsets
```

**Pay attention to trailing dots!**

Ex. for clouds like ultradns, the zone parameter is the zone name.

```
/zones/denominator.io./recordsets
```

Where for clouds like route53, you'd use the id.

```
/zones/Z1V14BIB35Q8HU/recordsets
```

#### GET /zones/{zoneIdOrName}/recordsets?name={name}&type={type}&qualifier={qualifier}
Returns a possibly empty array of your record sets.

Supported Query params:
  * name - optional - ex. `www.domain.com.`
  * type - optional unless you specify name - ex. `A`
  * qualifier - optional unless you specify type - ex. `US-West`

Ex. for route53, where the zone has an id

```bash
$ curl http://localhost:8080/zones/Z1V14BIB35Q8HU/recordsets
[
  {
    "name": "denominator.io.",
    "type": "NS",
    "ttl": 172800,
    "records": [
      {
        "nsdname": "ns-1707.awsdns-21.co.uk."
      },
      {
        "nsdname": "ns-1359.awsdns-41.org."
      },
      {
        "nsdname": "ns-981.awsdns-58.net."
      },
      {
        "nsdname": "ns-86.awsdns-10.com."
      }
    ]
  },
--snip--
```

Ex. refining results by name, type, and qualifier.

```bash
$ curl 'http://localhost:8080/zones/denominator.io./recordsets?name=www2.geo.denominator.io.&type=A&qualifier=alazona'
[
  {
    "name": "www2.geo.denominator.io.",
    "type": "A",
    "qualifier": "alazona",
    "ttl": 300,
    "records": [
      {
        "address": "192.0.2.1"
      }
    ],
    "geo": {
      "regions": {
        "United States (US)": [
          "Alaska",
          "Arizona"
        ]
      }
    }
  }
]
```

#### PUT /zones/{zoneIdOrName}/recordsets
Adds or replaces a record set and returns `204`.

Ex. to add or replace an MX record.
```bash
$ curl -X PUT http://localhost:8080/zones/Z3I0BTR7N27QRM/recordsets -d'{
  "name": "test.myzone.com.",
  "type": "TXT",
  "ttl": 1800,
  "records": [{
    "txtdata": "made in norway"
  }, {
    "txtdata": "made in sweden"
  }]
}'
```

#### DELETE /zones/{zoneIdOrName}/recordsets?name={name}&type={type}&qualifier={qualifier}
Deletes a record set if present and returns `204`.

Supported Query params:
  * name - required - ex. `www.domain.com.`
  * type - required - ex. `A`
  * qualifier - required if an advanced record set - ex. `US-West`

Ex. to delete a normal MX record

```bash
$ curl -X DELETE 'http://localhost:8080/zones/Z3I0BTR7N27QRM/recordsets?name=test.myzone.com.&type=TXT'
```
