package net.openhft.chronicle.engine;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.OS;
import net.openhft.chronicle.core.pool.ClassAliasPool;
import net.openhft.chronicle.engine.api.EngineReplication;
import net.openhft.chronicle.engine.api.map.KeyValueStore;
import net.openhft.chronicle.engine.api.map.MapView;
import net.openhft.chronicle.engine.api.tree.AssetTree;
import net.openhft.chronicle.engine.fs.ChronicleMapGroupFS;
import net.openhft.chronicle.engine.fs.FilePerKeyGroupFS;
import net.openhft.chronicle.engine.map.CMap2EngineReplicator;
import net.openhft.chronicle.engine.map.ChronicleMapKeyValueStore;
import net.openhft.chronicle.engine.map.VanillaMapView;
import net.openhft.chronicle.engine.server.ServerEndpoint;
import net.openhft.chronicle.engine.tree.VanillaAssetTree;
import net.openhft.chronicle.network.TCPRegistry;
import net.openhft.chronicle.wire.Wire;
import net.openhft.chronicle.wire.WireType;
import net.openhft.chronicle.wire.YamlLogging;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static org.junit.Assert.assertNotNull;

/**
 * Created by Rob Austin
 */
@RunWith(Parameterized.class)
public class ReplicationTest3Way {


    @Parameterized.Parameters
    public static List<Object[]> data() {
        return Arrays.asList(new Object[10][0]);
    }

    public ReplicationTest3Way() {
    }


    public static final WireType WIRE_TYPE = WireType.TEXT;
    public static final String NAME = "/ChMaps/test";
    public ServerEndpoint serverEndpoint1;
    public ServerEndpoint serverEndpoint2;
    public ServerEndpoint serverEndpoint3;
    private AssetTree tree3;
    private AssetTree tree1;
    private AssetTree tree2;

    @Before
    public void before() throws IOException {
        YamlLogging.clientWrites = true;
        YamlLogging.clientReads = true;

        ClassAliasPool.CLASS_ALIASES.addAlias(ChronicleMapGroupFS.class);
        ClassAliasPool.CLASS_ALIASES.addAlias(FilePerKeyGroupFS.class);
        //Delete any files from the last run
        Files.deleteIfExists(Paths.get(OS.TARGET, NAME));

        TCPRegistry.createServerSocketChannelFor("host.port1", "host.port2", "host.port3");

        WireType writeType = WireType.TEXT;
        tree3 = create(3, writeType);
        tree2 = create(2, writeType);
        tree1 = create(1, writeType);

        serverEndpoint3 = new ServerEndpoint("host.port3", tree3, writeType);
        serverEndpoint2 = new ServerEndpoint("host.port2", tree2, writeType);
        serverEndpoint1 = new ServerEndpoint("host.port1", tree1, writeType);
    }

    @After
    public void after() throws IOException {
        if (serverEndpoint1 != null)
            serverEndpoint1.close();
        if (serverEndpoint2 != null)
            serverEndpoint2.close();
        if (serverEndpoint3 != null)
            serverEndpoint3.close();
        if (tree1 != null)
            tree1.close();
        if (tree2 != null)
            tree2.close();
        if (tree2 != null)
            tree3.close();
        TCPRegistry.reset();
        // TODO TCPRegistery.assertAllServersStopped();
    }

    @NotNull
    private static AssetTree create(final int hostId, Function<Bytes, Wire> writeType) {
        AssetTree tree = new VanillaAssetTree((byte) hostId)
                .forTesting()
                .withConfig(resourcesDir() + "/cmkvst", OS.TARGET + "/" + hostId);

        tree.root().addWrappingRule(MapView.class, "map directly to KeyValueStore",
                VanillaMapView::new,
                KeyValueStore.class);
        tree.root().addLeafRule(EngineReplication.class, "Engine replication holder",
                CMap2EngineReplicator::new);
        tree.root().addLeafRule(KeyValueStore.class, "KVS is Chronicle Map", (context, asset) ->
                new ChronicleMapKeyValueStore(context.wireType(writeType),
                        asset));

      //  VanillaAssetTreeEgMain.registerTextViewofTree("host " + hostId, tree);

        return tree;
    }

    @NotNull
    public static String resourcesDir() {
        String path = ChronicleMapKeyValueStoreTest.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (path == null)
            return ".";
        return new File(path).getParentFile().getParentFile() + "/src/test/resources";
    }

    @Test
    public void test() throws InterruptedException {

//      YamlLogging.showServerWrites = true;
//      YamlLogging.showServerReads = true;

        final ConcurrentMap<String, String> map1 = tree1.acquireMap(NAME, String.class, String
                .class);
        assertNotNull(map1);

        final ConcurrentMap<String, String> map2 = tree2.acquireMap(NAME, String.class, String
                .class);
        assertNotNull(map2);

        final ConcurrentMap<String, String> map3 = tree3.acquireMap(NAME, String.class, String
                .class);

     //  Jvm.pause(200);
        map1.put("hello1", "world1");
        map2.put("hello2", "world2");
        map3.put("hello3", "world3");
  // Jvm.pause(2);
        for (int i = 1; i <= 100; i++) {
            if (map1.size() == 3 && map2.size() == 3 && map3.size() == 3)
                break;
            Jvm.pause(200);
        }


        Assert.assertEquals("world1", map1.get("hello1"));
        Assert.assertEquals("world2", map1.get("hello2"));
        Assert.assertEquals("world3", map1.get("hello3"));

        Assert.assertEquals("world1", map2.get("hello1"));
        Assert.assertEquals("world2", map2.get("hello2"));
        Assert.assertEquals("world3", map2.get("hello3"));

        Assert.assertEquals("world1", map3.get("hello1"));
        Assert.assertEquals("world2", map3.get("hello2"));
        Assert.assertEquals("world3", map3.get("hello3"));

    }


}



