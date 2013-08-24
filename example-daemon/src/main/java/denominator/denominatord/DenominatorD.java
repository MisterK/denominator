package denominator.denominatord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.ObjectGraph;
import dagger.Provides;
import denominator.Provider;
import denominator.Providers;
import feign.Logger;
import io.undertow.Undertow;

import static denominator.CredentialsConfiguration.anonymous;
import static denominator.CredentialsConfiguration.credentials;
import static denominator.Providers.instantiateModule;
import static denominator.Providers.provide;
import static denominator.common.Preconditions.checkArgument;

/**
 * Presents a {@link DenominatorDApi REST api} to users, by default listening on port 8080.
 */
public class DenominatorD {

  private static final org.jboss.logging.Logger
      log =
      org.jboss.logging.Logger.getLogger(DenominatorD.class);

  public static final String SYNTAX = "syntax: provider credentialArg1 credentialArg2 ...";

  @Inject
  Undertow server;

  public static void main(final String... args) {
    System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
    long s = System.nanoTime();
    checkArgument(args.length > 0, SYNTAX);
    String portOverride = System.getenv("DENOMINATORD_PORT");
    int port = portOverride != null ? Integer.parseInt(portOverride) : 8080;
    Provider provider = providerFromArgs(args[0]);
    System.out.println("targeting " + provider);

    Object[] modulesForGraph = new Object[5];
    modulesForGraph[0] = provide(provider);
    modulesForGraph[1] = instantiateModule(provider);
    modulesForGraph[2] = credentialsFromArgs(args);
    modulesForGraph[3] = new LogOutboundHttp();
    modulesForGraph[4] = new DenominatorDeps(port);
    DenominatorD daemon = ObjectGraph.create(modulesForGraph).get(DenominatorD.class);

    daemon.server.start();
    System.out.println("listening on http://localhost:" + port);
    System.out
        .println("initialized in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - s) + "ms");
  }

  static Object credentialsFromArgs(String[] args) {
    switch (args.length) {
      case 4:
        return credentials(args[1], args[2], args[3]);
      case 3:
        return credentials(args[1], args[2]);
      case 1:
        return anonymous();
      default:
        throw new IllegalArgumentException(SYNTAX);
    }
  }

  static Provider providerFromArgs(String providerName) {
    List<String> providerNames = new ArrayList<String>();
    for (Provider p : Providers.list()) {
      if (p.name().equals(providerName)) {
        return p;
      }
      providerNames.add(p.name());
    }
    throw new IllegalArgumentException("provider " + providerName + " not in " + providerNames);
  }

  @Module(library = true, overrides = true)
  static class LogOutboundHttp {

    @Provides
    Logger.Level provideLevel() {
      return Logger.Level.BASIC;
    }

    @Provides
    Logger logger() {
      return new Logger() {
        @Override
        protected void log(String configKey, String format, Object... args) {
          log.infof(methodTag(configKey) + format, args);
        }
      };
    }
  }

  @Module(injects = DenominatorD.class, complete = false)
  static class DenominatorDeps {

    private final int port;

    DenominatorDeps(int port) {
      this.port = port;
    }

    @Provides
    @Singleton
    Gson gson() {
      return new GsonBuilder().setPrettyPrinting().create();
    }

    @Provides
    @Singleton
    Undertow server(DenominatorDRouter apiEntryPoint) {
      return Undertow.builder().addHttpListener(port, "localhost").setHandler(apiEntryPoint).build();
    }
  }
}
