package com.maxisvest.fabric.util;

import com.maxisvest.fabric.bean.bo.Chaincode;
import com.maxisvest.fabric.bean.bo.Orderers;
import com.maxisvest.fabric.bean.bo.Peers;
import com.maxisvest.fabric.blockchain.ChaincodeManager;
import com.maxisvest.fabric.blockchain.FabricConfig;
import com.maxisvest.fabric.constant.Constant;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.exception.TransactionException;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Slf4j
public class FabricManager {

    private static Logger log = Logger.getLogger(FabricManager.class);
    private ChaincodeManager manager;

    private static FabricManager instance = null;

    public static FabricManager obtain(String chaincodeName, String chaincodeVersion)
            throws CryptoException, InvalidArgumentException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException, TransactionException, IOException {
        if (null == instance) {
            synchronized (FabricManager.class) {
                if (null == instance) {
                    instance = new FabricManager(chaincodeName, chaincodeVersion);
                }
            }
        }
        return instance;
    }

    private FabricManager(String chaincodeName, String chaincodeVersion)
            throws CryptoException, InvalidArgumentException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException, TransactionException, IOException {
        FabricConfig config = getConfig(chaincodeName, chaincodeVersion);
        try {
            manager = new ChaincodeManager(config);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取节点服务器管理器
     *
     * @return 节点服务器管理器
     */
    public ChaincodeManager getManager() {
        return manager;
    }

    /**
     * 根据节点作用类型获取节点服务器配置
     *
     * @param //type 服务器作用类型（1、执行；2、查询）
     * @param chaincodeName
     * @param chaincodeVersion
     * @return 节点服务器配置
     */
    private FabricConfig getConfig(String chaincodeName, String chaincodeVersion) {
        FabricConfig config = new FabricConfig();
        config.setOrderers(getOrderers());
        config.setPeers(getPeers());
        config.setChaincode(getChaincode("mychannel", chaincodeName,
                "/Users/admin/IdeaProjects/fabric-java-sample/src/main/test/fixture/sdkintegration/javacc/sample1", chaincodeVersion));
        config.setChannelArtifactsPath(getChannleArtifactsPath());
        config.setCryptoConfigPath(getCryptoConfigPath());
        return config;
    }

    private Orderers getOrderers() {
        Orderers orderer = new Orderers();
        orderer.setOrdererDomainName("example.com");
        // orderer.addOrderer("orderer.example.com", "grpc://47.98.143.199:7050");
        orderer.addOrderer("orderer.example.com", "grpc://127.0.0.1:7050");
       // orderer.addOrderer("orderer2.example.com", "grpc://x.x.x.xxx:7050");
        return orderer;
    }

    /**
     * 获取节点服务器集
     *
     * @return 节点服务器集
     */
    private Peers getPeers() {
        Peers peers = new Peers();
        peers.setOrgName("Org1");
        peers.setOrgMSPID("Org1MSP");
        peers.setOrgDomainName("org1.example.com");
        peers.addPeer("peer0.org1.example.com", "peer0.org1.example.com",
                "grpc://localhost:7051", "grpc://localhost:7053", "http://localhost:7054");
        peers.addPeer("peer1.org1.example.com", "peer1.org1.example.com",
                "grpc://localhost:7056", "grpc://localhost:7058", "http://localhost:7054");
        return peers;
    }

    /**
     * 获取智能合约
     *
     * @param channelName      频道名称
     * @param chaincodeName    智能合约名称
     * @param chaincodePath    智能合约路径
     * @param chaincodeVersion 智能合约版本
     * @return 智能合约
     */
    private Chaincode getChaincode(String channelName, String chaincodeName, String chaincodePath, String chaincodeVersion) {
        Chaincode chaincode = new Chaincode();
        chaincode.setChannelName(channelName);
        chaincode.setChaincodeName(chaincodeName);
        chaincode.setChaincodePath(chaincodePath);
        chaincode.setChaincodeVersion(chaincodeVersion);
        chaincode.setInvokeWatiTime(100000);
        chaincode.setDeployWatiTime(120000);
        return chaincode;
    }

    /**
     * 获取channel-artifacts配置路径
     *
     * @return /WEB-INF/classes/fabric/channel-artifacts/
     */
    private String getChannleArtifactsPath() {
        String directorys = FabricManager.class.getClassLoader().getResource("fabric").getFile();
        log.debug("directorys = " + directorys);
        File directory = new File(directorys);
        log.debug("directory = " + directory.getPath());

        return directory.getPath() + "/channel-artifacts/";
    }

    /**
     * 获取crypto-config配置路径
     *
     * @return /WEB-INF/classes/fabric/crypto-config/
     */
    private String getCryptoConfigPath() {
        String directorys = FabricManager.class.getClassLoader().getResource("fabric").getFile();
        log.debug("directorys = " + directorys);
        File directory = new File(directorys);
        log.debug("directory = " + directory.getPath());

        return directory.getPath() + "/crypto-config/";
    }

    public static void main(String[] args) {
        try {
            //
            // List<Object> str2 = Arrays.asList("a", new HashMap<>());
            // byte[] serialize = serialize(str2);
            // Object deserialize = deserialize(serialize, Object.class);

            ChaincodeManager manager = FabricManager.obtain("asset", Constant.ASSET_CHAINCODE_VERSION).getManager();

            manager.installChainCode();
            manager.instantiateChainCode();
            // manager.upgradeChaincode();
            // get(manager);
            set(manager);
            // geth(manager);
            // manager.enrollUser("aaa");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void get(ChaincodeManager manager) throws InvalidArgumentException, NoSuchAlgorithmException, IOException, NoSuchProviderException, TransactionException, ProposalException, CryptoException, InvalidKeySpecException {
        List<Object> str = Arrays.asList("a");
        Map<String, Object> query = manager.query("get", SerializeUtil.serialize(str));
    }

    public static void geth(ChaincodeManager manager) throws InvalidArgumentException, NoSuchAlgorithmException, IOException, NoSuchProviderException, TransactionException, ProposalException, CryptoException, InvalidKeySpecException {
        List<Object> str = Arrays.asList("a");
        Map<String, Object> query = manager.query("geth", SerializeUtil.serialize(str));
    }

    public static void set(ChaincodeManager manager) throws IOException, NoSuchAlgorithmException, ProposalException, ExecutionException, TimeoutException, InterruptedException, InvalidArgumentException, TransactionException, CryptoException, NoSuchProviderException, InvalidKeySpecException {
        HashMap<Object, Object> objectObjectHashMap = new HashMap<>();
        byte[] bytes = FileUtils.readFileToByteArray(new File("/Users/admin/Desktop/HTTP Request.jmx"));
        objectObjectHashMap.put("file", bytes);
        List<Object> str = Arrays.asList("a", objectObjectHashMap);
        Map<String, Object> result = manager.invoke("set", SerializeUtil.serialize(str));
    }

}