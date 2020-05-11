package com.maxisvest.fabric.blockchain;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.maxisvest.fabric.bean.bo.Chaincode;
import com.maxisvest.fabric.bean.bo.Orderers;
import com.maxisvest.fabric.bean.bo.Peers;
import com.maxisvest.fabric.util.SerializeUtil;
import org.apache.log4j.Logger;
import org.hyperledger.fabric.protos.peer.FabricProposalResponse;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.exception.TransactionException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.HFCAClient;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;


public class ChaincodeManager {

    private static Logger log = Logger.getLogger(ChaincodeManager.class);

    private FabricConfig config;
    private Orderers orderers;
    private Peers peers;
    private Chaincode chaincode;

    private HFClient client;
    private FabricOrg fabricOrg;
    private Channel channel;
    private ChaincodeID chaincodeID;

    public ChaincodeManager(FabricConfig fabricConfig)
            throws Exception {
        this.config = fabricConfig;

        orderers = this.config.getOrderers();
        peers = this.config.getPeers();
        chaincode = this.config.getChaincode();

        client = HFClient.createNewInstance();
        log.debug("Create instance of HFClient");
        try {
            client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        } catch (Exception e) {
            e.printStackTrace();
        }
        log.debug("Set Crypto Suite of HFClient");

        fabricOrg = getFabricOrg();
        channel = getChannel();
        chaincodeID = getChaincodeID();

        client.setUserContext(fabricOrg.getPeerAdmin()); // 也许是1.0.0测试版的bug，只有节点管理员可以调用链码
    }

    public void enrollUser(String userName) throws Exception {
        HFCAClient caClient = fabricOrg.getCAClient();
        FabricStore fabricStore = fabricOrg.getFabricStore();
        FabricUser peerAdmin = fabricOrg.getPeerAdmin();
        caClient.enroll("aaa", "aaa");
        // Enrollment adminEnroll = caClient.enroll("admin", "adminpw");
        //
        // FabricUser newMember = fabricStore.getMember(userName, peers.getOrgName());
        // newMember.setEnrollment(adminEnroll);
        // RegistrationRequest aaa = new RegistrationRequest(userName);
        // aaa.setSecret("aaa");
        // String registerSecret = caClient.register(aaa, newMember);
        // Enrollment enroll = caClient.enroll(userName, registerSecret);
        // newMember.setEnrollment(enroll);
    }

    private FabricOrg getFabricOrg() throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException, IOException {

        // java.io.tmpdir : C:\Users\yangyi47\AppData\Local\Temp\
        File storeFile = new File(System.getProperty("java.io.tmpdir") + "/HFCSampletest.properties");
        FabricStore fabricStore = new FabricStore(storeFile);
        // Get Org1 from configuration
        FabricOrg fabricOrg = new FabricOrg(peers, orderers, fabricStore, config.getCryptoConfigPath());
        log.debug("Get FabricOrg");
        return fabricOrg;
    }

    private Channel getChannel()
            throws Exception {
        client.setUserContext(fabricOrg.getPeerAdmin());
        return getChannel(fabricOrg, client);
    }

    private Channel getChannel(FabricOrg fabricOrg, HFClient client) throws Exception {
        File file = new File("channel.block");

        Channel channel = client.getChannel(chaincode.getChannelName());
        if (channel != null) {
            return channel;
        }

        FabricUser peerAdmin = fabricOrg.getPeerAdmin();
        String path =config.getChannelArtifactsPath() + "/mychannel.tx";
        ChannelConfiguration channelConfiguration = new ChannelConfiguration(new File(path));

        //Create channel that has only one signer that is this orgs peer admin. If channel creation policy needed more signature they would need to be added too.
        // Channel channel = client.newChannel(chaincode.getChannelName());
        log.debug("Get Chain " + chaincode.getChannelName());

//        channel.setTransactionWaitTime(chaincode.getInvokeWatiTime());
//        channel.setDeployWaitTime(chaincode.getDeployWatiTime());

        Orderer anOrderer = null;
        for (int i = 0; i < orderers.get().size(); i++) {
            File ordererCert = Paths.get(config.getCryptoConfigPath(), "/ordererOrganizations", orderers.getOrdererDomainName(), "orderers", orderers.get().get(i).getOrdererName(),
                    "tls/server.crt").toFile();
            if (!ordererCert.exists()) {
                throw new RuntimeException(
                        String.format("Missing cert file for: %s. Could not find at location: %s", orderers.get().get(i).getOrdererName(), ordererCert.getAbsolutePath()));
            }
            Properties ordererProperties = new Properties();
            ordererProperties.setProperty("pemFile", ordererCert.getAbsolutePath());
            ordererProperties.setProperty("hostnameOverride", orderers.getOrdererDomainName());
            ordererProperties.setProperty("sslProvider", "openSSL");
            ordererProperties.setProperty("negotiationType", "TLS");
            ordererProperties.put("grpc.ManagedChannelBuilderOption.maxInboundMessageSize", 9000000);
            ordererProperties.setProperty("ordererWaitTimeMilliSecs", "300000");
            anOrderer = client.newOrderer(orderers.get().get(i).getOrdererName(), fabricOrg.getOrdererLocation(orderers.get().get(i).getOrdererName()), ordererProperties);
            // channel.addOrderer(
            //         anOrderer);
        }

        List<Peer> ps = new ArrayList<>();
        for (int i = 0; i < peers.get().size(); i++) {
            File peerCert = Paths.get(config.getCryptoConfigPath(), "/peerOrganizations", peers.getOrgDomainName(), "peers", peers.get().get(i).getPeerName(), "tls/server.crt")
            // File peerCert = Paths.get(config.getCryptoConfigPath(), "/peerOrganizations", peers.getOrgDomainName(), "peers", peers.get().get(i).getPeerName(), "tls/ca.crt")
                    .toFile();
            if (!peerCert.exists()) {
                throw new RuntimeException(
                        String.format("Missing cert file for: %s. Could not find at location: %s", peers.get().get(i).getPeerName(), peerCert.getAbsolutePath()));
            }
            Properties peerProperties = new Properties();
            peerProperties.setProperty("pemFile", peerCert.getAbsolutePath());
            peerProperties.setProperty("trustServerCertificate", "true"); //testing
            // environment only NOT FOR PRODUCTION!
            peerProperties.setProperty("hostnameOverride", peers.getOrgDomainName());
            peerProperties.setProperty("sslProvider", "openSSL");
            peerProperties.setProperty("negotiationType", "TLS");
            // 在grpc的NettyChannelBuilder上设置特定选项
            peerProperties.put("grpc.ManagedChannelBuilderOption.maxInboundMessageSize", 9000000);
            Peer peer = client.newPeer(peers.get().get(i).getPeerName(), fabricOrg.getPeerLocation(peers.get().get(i).getPeerName()), peerProperties);
            ps.add(peer);
            // channel.joinPeer(peer);
            if (peers.get().get(i).isAddEventHub()) {
                this.channel.addEventHub(
                        client.newEventHub(peers.get().get(i).getPeerEventHubName(), fabricOrg.getEventHubLocation(peers.get().get(i).getPeerEventHubName()), peerProperties));
            }
        }
        if (file.exists()) {
            this.channel = client.deSerializeChannel(file).initialize();
            for (Peer p : ps) {
                this.channel.addPeer(p);
            }
        } else {
            this.channel = client.newChannel(chaincode.getChannelName(), anOrderer, channelConfiguration, client.getChannelConfigurationSignature(channelConfiguration, peerAdmin));
            this.channel.addOrderer(anOrderer);
            log.debug("channel.isInitialized() = " + this.channel.isInitialized());
            if (!this.channel.isInitialized()) {
                this.channel.initialize();

                this.channel.serializeChannel(file);
                for (Peer p : ps) {
                    Set<String> strings = client.queryChannels(p);
                    if (!strings.contains(this.channel.getName())) {
                        this.channel.joinPeer(p);
                    }
                }
            }
        }

        if (config.isRegisterEvent()) {
            this.channel.registerBlockListener(new BlockListener() {

                @Override
                public void received(BlockEvent event) {
                    // TODO
                    log.debug("========================Event事件监听开始========================");
                    try {
                        log.debug("event.getChannelId() = " + event.getChannelId());
                        // log.debug("event.getEvent().getChaincodeEvent().getPayload().toStringUtf8() = " + event.getEvent().getChaincodeEvent().getPayload().toStringUtf8());
                        log.debug("event.getBlock().getData().getDataList().size() = " + event.getBlock().getData().getDataList().size());
                        ByteString byteString = event.getBlock().getData().getData(0);
                        String result = byteString.toStringUtf8();
                        log.debug("byteString.toStringUtf8() = " + result);

                        String r1[] = result.split("END CERTIFICATE");
                        String rr = r1[2];
                        log.debug("rr = " + rr);
                    } catch (InvalidProtocolBufferException e) {
                        // TODO
                        e.printStackTrace();
                    }
                    log.debug("========================Event事件监听结束========================");
                }
            });
        }

        return this.channel;
    }

    private ChaincodeID getChaincodeID() {
        return ChaincodeID.newBuilder().setName(chaincode.getChaincodeName()).setVersion(chaincode.getChaincodeVersion()).setPath(chaincode.getChaincodePath()).build();
    }

    public Collection<ProposalResponse> installChainCode() throws InvalidArgumentException, ProposalException, IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchMethodException, CryptoException, ClassNotFoundException, IOException {

        InstallProposalRequest installProposalRequest = client.newInstallProposalRequest();
        Chaincode chaincode = config.getChaincode();
        String chaincodeVersion = config.getChaincode().getChaincodeVersion();
        ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(chaincode.getChaincodeName()).setVersion(chaincodeVersion).build();
        installProposalRequest.setChaincodeID(chaincodeID);
        installProposalRequest.setChaincodeVersion(chaincodeVersion);
        installProposalRequest.setChaincodeLanguage(TransactionRequest.Type.JAVA);
           installProposalRequest.setChaincodeSourceLocation(new File(chaincode.getChaincodePath()));

            // installProposalRequest.setChaincodePath(chainCodeName);
//            System.out.println("Paths>>>>>>>>>>>>>>>"+Paths.get(chainCodePath,projectName).toFile());
//            installProposalRequest.setChaincodeInputStream(Util.generateTarGzInputStream(Paths.get(chainCodePath,projectName).toFile(),"src"));
        Collection<ProposalResponse> proposalResponses = client.sendInstallProposal(installProposalRequest, channel.getPeers());
        for (ProposalResponse proposalRespons : proposalResponses) {
            System.out.println(proposalRespons.getStatus().getStatus());
            System.out.println(proposalRespons.getMessage());
        }
        return proposalResponses;
    }

    public Collection<ProposalResponse> instantiateChainCode() throws ProposalException, InvalidArgumentException, InterruptedException, ExecutionException, TimeoutException, IOException {

        Chaincode chaincode = config.getChaincode();
        String chaincodeVersion = chaincode.getChaincodeVersion();
        InstantiateProposalRequest instantiateProposalRequest = client.newInstantiationProposalRequest();
        instantiateProposalRequest.setProposalWaitTime(12000000);
        ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(chaincode.getChaincodeName()).setVersion(chaincodeVersion).setPath(chaincode.getChaincodePath()).build();
        instantiateProposalRequest.setChaincodeID(chaincodeID);
        instantiateProposalRequest.setChaincodeLanguage(TransactionRequest.Type.JAVA);
        instantiateProposalRequest.setFcn("init");
        instantiateProposalRequest.setArgs("");
        instantiateProposalRequest.setArgs(SerializeUtil.serialize(Arrays.asList("a", new HashMap<>())));

        //region 如果需要用到私有数据，需要添加私有数据的配置
        //instantiateProposalRequest.setChaincodeCollectionConfiguration(ChaincodeCollectionConfiguration.fromYamlFile(new File("src/test/fixture/collectionProperties/PrivateDataIT.yaml")));
        //endregion

        Map<String, byte[]> tm = new HashMap<>();
        tm.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(UTF_8));
        tm.put("method", "InstantiateProposalRequest".getBytes(UTF_8));
        instantiateProposalRequest.setTransientMap(tm);
        // if (!endorsementPolicy.equals("")){
        //     ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
        //     chaincodeEndorsementPolicy.fromYamlFile(new File(endorsementPolicy));
        //     instantiateProposalRequest.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);
        // }
        Collection<ProposalResponse> responses = channel.sendInstantiationProposal(instantiateProposalRequest,channel.getPeers());

        List<ProposalResponse> list = responses.stream().filter(ProposalResponse::isVerified).collect(Collectors.toList());
        if (list.size() == 0) {
            return responses;
        }
        BlockEvent.TransactionEvent event = channel.sendTransaction(responses).get(60, TimeUnit.SECONDS);

        if (event.isValid()){
            log.info("InstantiateChainCode success");
        }

        return responses;
    }

    public Collection<ProposalResponse> upgradeChaincode() throws ProposalException, InvalidArgumentException, InterruptedException, ExecutionException, TimeoutException, IOException {

        Chaincode chaincode = config.getChaincode();
        String chaincodeVersion = chaincode.getChaincodeVersion();
        UpgradeProposalRequest upgradeProposalRequest = client.newUpgradeProposalRequest();
        upgradeProposalRequest.setProposalWaitTime(12000000);
        ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(chaincode.getChaincodeName()).setVersion(chaincodeVersion).setPath(chaincode.getChaincodePath()).build();
        upgradeProposalRequest.setChaincodeID(chaincodeID);
        upgradeProposalRequest.setChaincodeLanguage(TransactionRequest.Type.JAVA);
        upgradeProposalRequest.setFcn("init");
        upgradeProposalRequest.setArgs("");
        upgradeProposalRequest.setArgs(SerializeUtil.serialize(Arrays.asList("a", new HashMap<>())));


        //region 如果需要用到私有数据，需要添加私有数据的配置
        //instantiateProposalRequest.setChaincodeCollectionConfiguration(ChaincodeCollectionConfiguration.fromYamlFile(new File("src/test/fixture/collectionProperties/PrivateDataIT.yaml")));
        //endregion

        Map<String, byte[]> tm = new HashMap<>();
        tm.put("HyperLedgerFabric", "UpgradeProposalRequest:JavaSDK".getBytes(UTF_8));
        tm.put("method", "UpgradeProposalRequest".getBytes(UTF_8));
        upgradeProposalRequest.setTransientMap(tm);
        // if (!endorsementPolicy.equals("")){
        //     ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
        //     chaincodeEndorsementPolicy.fromYamlFile(new File(endorsementPolicy));
        //     instantiateProposalRequest.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);
        // }
        Collection<ProposalResponse> responses = channel.sendUpgradeProposal(upgradeProposalRequest,channel.getPeers());

        List<ProposalResponse> list = responses.stream().filter(ProposalResponse::isVerified).collect(Collectors.toList());
        if (list.size() == 0) {
            return responses;
        }
        BlockEvent.TransactionEvent event = channel.sendTransaction(responses).get(60, TimeUnit.SECONDS);

        if (event.isValid()){
            log.info("upgradeProposalRequest success");
        }

        return responses;
    }


    public Map<String, Object> invoke(String fcn, byte[] args)
            throws InvalidArgumentException, ProposalException, InterruptedException, ExecutionException, TimeoutException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException, CryptoException, TransactionException, IOException {
        Map<String, Object> resultMap = new HashMap<>();

        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();

        /// Send transaction proposal to all peers
        TransactionProposalRequest transactionProposalRequest = client.newTransactionProposalRequest();
        transactionProposalRequest.setChaincodeID(chaincodeID);
        transactionProposalRequest.setFcn(fcn);
        transactionProposalRequest.setArgs(args);

        Map<String, byte[]> tm2 = new HashMap<>();
        tm2.put("HyperLedgerFabric", "TransactionProposalRequest:JavaSDK".getBytes(UTF_8));
        tm2.put("method", "TransactionProposalRequest".getBytes(UTF_8));
        tm2.put("result", ":)".getBytes(UTF_8));
        transactionProposalRequest.setTransientMap(tm2);

        Collection<ProposalResponse> transactionPropResp = channel.sendTransactionProposal(transactionProposalRequest, channel.getPeers());
        for (ProposalResponse response : transactionPropResp) {
            if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                successful.add(response);
            } else {
                failed.add(response);
            }
        }

        Collection<Set<ProposalResponse>> proposalConsistencySets = SDKUtils.getProposalConsistencySets(transactionPropResp);
        if (proposalConsistencySets.size() != 1) {
            log.error("Expected only one set of consistent proposal responses but got " + proposalConsistencySets.size());
        }

        if (failed.size() > 0) {
            ProposalResponse firstTransactionProposalResponse = failed.iterator().next();
            log.error("Not enough endorsers for inspect:" + failed.size() + " endorser error: " + firstTransactionProposalResponse.getMessage() + ". Was verified: "
                    + firstTransactionProposalResponse.isVerified());
            resultMap.put("code", "error");
            resultMap.put("data", firstTransactionProposalResponse.getMessage());
            return resultMap;
        } else {
            log.info("Successfully received transaction proposal responses.");
            ProposalResponse resp = transactionPropResp.iterator().next();
            byte[] x = resp.getChaincodeActionResponsePayload();
            Object deserialize = null;
            if (x != null && x.length != 0) {
                deserialize = SerializeUtil.deserialize(x, Object.class);
            }
            channel.sendTransaction(successful);
            resultMap.put("code", "success");
            resultMap.put("data", deserialize);
            return resultMap;
        }

//        channel.sendTransaction(successful).thenApply(transactionEvent -> {
//            if (transactionEvent.isValid()) {
//                log.info("Successfully send transaction proposal to orderer. Transaction ID: " + transactionEvent.getTransactionID());
//            } else {
//                log.info("Failed to send transaction proposal to orderer");
//            }
//            // chain.shutdown(true);
//            return transactionEvent.getTransactionID();
//        }).get(chaincode.getInvokeWatiTime(), TimeUnit.SECONDS);
    }

    public Map<String, Object> query(String fcn, byte[] args) throws InvalidArgumentException, ProposalException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException, CryptoException, TransactionException, IOException {
        Map<String, Object> resultMap = new HashMap<>();
        String payload = "";
        QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
        queryByChaincodeRequest.setArgs(args);
        queryByChaincodeRequest.setFcn(fcn);
        queryByChaincodeRequest.setChaincodeID(chaincodeID);

        Map<String, byte[]> tm2 = new HashMap<>();
        tm2.put("HyperLedgerFabric", "QueryByChaincodeRequest:JavaSDK".getBytes(UTF_8));
        tm2.put("method", "QueryByChaincodeRequest".getBytes(UTF_8));
        queryByChaincodeRequest.setTransientMap(tm2);

        Collection<ProposalResponse> queryProposals = channel.queryByChaincode(queryByChaincodeRequest, channel.getPeers());
        for (ProposalResponse proposalResponse : queryProposals) {
            if (!proposalResponse.isVerified() || proposalResponse.getStatus() != ProposalResponse.Status.SUCCESS) {
                log.debug("Failed query proposal from peer " + proposalResponse.getPeer().getName() + " status: " + proposalResponse.getStatus() + ". Messages: "
                        + proposalResponse.getMessage() + ". Was verified : " + proposalResponse.isVerified());
                resultMap.put("code", "error");
                resultMap.put("data", "Failed query proposal from peer " + proposalResponse.getPeer().getName() + " status: " + proposalResponse.getStatus() + ". Messages: "
                        + proposalResponse.getMessage() + ". Was verified : " + proposalResponse.isVerified());
            } else {
                FabricProposalResponse.Response response = proposalResponse.getProposalResponse().getResponse();
                String message = response.getMessage();
                byte[] bytes = response.getPayload().toByteArray();
                Object deserialize = SerializeUtil.deserialize(bytes, Object.class);
                log.debug("Query payload from peer: " + proposalResponse.getPeer().getName());
                log.debug("" + payload);
                resultMap.put("message", message);
                resultMap.put("code", "success");
                resultMap.put("data", deserialize);
            }
        }
        return resultMap;
    }

    public Map<String, Object> setContent(String userID, String contentType, Object content) throws IOException, NoSuchAlgorithmException, ProposalException, ExecutionException, TimeoutException, InterruptedException, InvalidArgumentException, TransactionException, CryptoException, NoSuchProviderException, InvalidKeySpecException {
        HashMap<Object, Object> objectObjectHashMap = new HashMap<>();
        objectObjectHashMap.put("contentType", contentType);
        objectObjectHashMap.put("content", content);

        List<Object> str = Arrays.asList(userID, objectObjectHashMap);
        return this.invoke("set", SerializeUtil.serialize(str));
    }

    public Map<String, Object> getContents(String userID) throws IOException, NoSuchAlgorithmException, ProposalException, ExecutionException, TimeoutException, InterruptedException, InvalidArgumentException, TransactionException, CryptoException, NoSuchProviderException, InvalidKeySpecException {

        List<Object> str = Arrays.asList(userID);
        return this.query("geth", SerializeUtil.serialize(str));
    }

}