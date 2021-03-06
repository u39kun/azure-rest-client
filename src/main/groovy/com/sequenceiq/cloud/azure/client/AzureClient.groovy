package com.sequenceiq.cloud.azure.client

import com.thoughtworks.xstream.io.HierarchicalStreamReader
import com.thoughtworks.xstream.io.HierarchicalStreamWriter
import com.thoughtworks.xstream.io.copy.HierarchicalStreamCopier
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver
import com.thoughtworks.xstream.io.xml.XppReader
import groovy.json.JsonSlurper
import groovyx.net.http.ContentType
import groovyx.net.http.HttpResponseDecorator
import groovyx.net.http.RESTClient
import groovy.json.JsonOutput

import static groovyx.net.http.ContentType.*

import javax.xml.stream.XMLStreamException
import groovyx.net.http.AuthConfig

/**
 * Azure cloud REST client - http://msdn.microsoft.com/library/azure/ee460799.aspx
 */
class AzureClient extends RESTClient {

    def subscriptionId = "id"
    def keyStorePath = "WindowsAzureKeyStore.jks"
    def keyStorePassword = "password"

    static final boolean debugEnabled = false;

    def JsonSlurper jsonSlurper = new JsonSlurper()

    def AzureClient() {
        AzureClient(subscriptionId, keyStorePath, keyStorePassword)
    }

    def AzureClient(String _subscriptionId, String _keyStorePath, String _keyStorePassword) {
        super(String.format("https://management.core.windows.net/%s/", _subscriptionId))
        subscriptionId = _subscriptionId
        keyStorePath = "file://" + _keyStorePath
        keyStorePassword = _keyStorePassword

        def authConfig = new AuthConfig(this)
        println("keyStorePath=" + keyStorePath)
        authConfig.certificate(keyStorePath, keyStorePassword)
        setAuthConfig(authConfig)
        setHeaders("x-ms-version": "2014-04-01")
        // setting json as the desired format does not seem to work and always defaults to XML
        // "Content-Type": "application/json", "Accept": "application/json"
    }

    def getSubscriptionId() {
        subscriptionId
    }

    def setSubscriptionId(_subscriptionId) {
        subscriptionId = _subscriptionId
    }

    def getKeyStorePath() {
        keyStorePath
    }

    def setKeyStorePath(_keyStorePath) {
        keyStorePath = _keyStorePath
    }

    def getKeyStorePassword() {
        keyStorePassword
    }

    def setKeyStorePassword(_keyStorePassword) {
        keyStorePassword = _keyStorePassword
    }

    static void main(String[] args) {

        def client

        if (args.length == 3) {
            client = new AzureClient(args[0], args[1], args[2])
        } else {
            println()
            println("Usage: AzureClient <subscription id> <absolute path for keystore> <password for keystore>")
            println()
            System.exit(1);
        }

        // List locations
        println "Listing all locations:"
        String jsonResponse = client.getLocations()
        if (jsonResponse != null) {
            println(JsonOutput.prettyPrint(jsonResponse))
        }

        // List virtual networks
        println "Listing all virtual networks:"
        jsonResponse = client.getVirtualNetworks()
        if (jsonResponse != null) {
            println(JsonOutput.prettyPrint(jsonResponse))
        }

        // List storage accounts
        println "Listing all storage accounts:"
        jsonResponse = client.getStorageAccounts()
        if (jsonResponse != null) {
            println(JsonOutput.prettyPrint(jsonResponse))
        }

        // List OS images
        println "Listing all OS images:"
        jsonResponse = client.getOsImages()
        if (jsonResponse != null) {
            println(JsonOutput.prettyPrint(jsonResponse))
        }

        // List VM Images
        println "Listing all VM images:"
        jsonResponse = client.getVmImages()
        if (jsonResponse != null) {
            println(JsonOutput.prettyPrint(jsonResponse))
        }

        // List Disks
        println "Listing all disks:"
        jsonResponse = client.getDisks()
        if (jsonResponse != null) {
            println(JsonOutput.prettyPrint(jsonResponse))
        }

        // List Affinity Groups
        println "Listing all affinity groups:"
        jsonResponse = client.getAffinityGroups()
        if (jsonResponse != null) {
            println(JsonOutput.prettyPrint(jsonResponse))
        }

        // List Cloud Services
        println "Listing all cloud services:"
        jsonResponse = client.getCloudServices()
        if (jsonResponse != null) {
            println(JsonOutput.prettyPrint(jsonResponse))
        }

    }

    /**
     * Overrides the RESTClient's get method behavior so that we display the output in JSON by default (XML output
     * can be specified via the extra "format" parameter).
     * This was needed because the Azure API does not seem to return JSON even though the client sets appropriate
     * HTTP headers.
     *
     * @param args: the same as what's accepted by groovyx.net.http.RESTClient, with an additional format which
     *   can be JSON or XML.  If format is not supplied, JSON is assumed.
     */
    def get(Map args) {
        if (args.format == JSON || args.format == null) {
            args.contentType = TEXT
            args.remove('format')
            return convert(super.get(args).data.text)
        } else if (args.format == XML) {
            args.contentType = TEXT
            args.remove('format')
            return super.get(args).data.text
        } else {
            throw new IllegalArgumentException("Unrecognized format " + args.format)
        }
    }

    def post(Map args) {
        def HttpResponseDecorator response = super.post(args)
        // If the server responds with 307 (Temporary Redirect), POST again after a short wait
        while (response.getStatus() == 307) {
            sleep(1000)
            response = super.post(args)
        }
        return response
    }

    /**
     * Gets the status of asynchronous requests.
     *
     * @param requestId
     * @return
     * Succeeded example: {"Operation":{"ID":"e9e74e80-5709-9cd2-8aaa-a5c9d238a12a","Status":"Succeeded","HttpStatusCode":200}}
     * In Progress example: {"Operation":{"ID":"e9e74e80-5709-9cd2-8aaa-a5c9d238a12a","Status":"InProgress"}}
     */
    def getRequestStatus(String requestId) {
        return get(path: "operations/" + requestId)
    }

    /**
     * Gets all locations available, such as "West US", "East Asia", etc.
     * @param format: JSON or XML
     */
    def getLocations(ContentType format = ContentType.JSON) {
        return get(path: "locations", format: format)
    }

    /**
     * Gets all affinity groups under the subscription.
     * @param format: JSON or XML
     */
    def getAffinityGroups(ContentType format = ContentType.JSON) {
        return get(path: "affinitygroups", format: format)
    }

    /**
     * Creates an affinity group.
     * This needs to be created before creating storage accounts, virtual networks, cloud services, virtual machines, and other resources.
     * @param
     *   name: the name of the affinity group to create
     *   description
     *   location: pick one from the output of getLocations(); e.g., "East US"
     */
    def createAffinityGroup(Map args) {
        return post(
                path: "affinitygroups",
                requestContentType: XML,
                body: {
                    mkp.xmlDeclaration()
                    CreateAffinityGroup(xmlns: "http://schemas.microsoft.com/windowsazure") {
                        Name(args.name)
                        Label(args.name.bytes.encodeBase64().toString())
                        Description(args.description)
                        Location(args.location)
                    }
                }
        )
    }

    /**
     * Deletes an affinity group.
     * @param args
     *   name: the name of the affinity group to delete.
     */
    def deleteAffinityGroup(Map args) {
        return delete(path: String.format('affinitygroups/%s', args.name))
    }

    /**
     * Gets all virtual network configurations for the subscription.
     * Used when creating a new virtual network.
     * @param format: JSON or XML
     */
    def getVirtualNetworkConfiguration(ContentType format = ContentType.JSON) {
        return get(path: "services/networking/media", format: format)
    }

    /**
     * Gets all virtual networks under the subscription.
     * @param format: JSON or XML
     */
    def getVirtualNetworks(ContentType format = ContentType.JSON) {
        return get(path: "services/networking/virtualnetwork", format: format)
    }

    /**
     * Creates a virtual network.
     * Note that this call is asynchronous.
     * If there are no validation errors, the server returns 202 (Accepted).
     * The request status can be checked via getRequestStatus(requestId).
     *
     * @param args
     *   name: required; name of the virtual network to create
     *   affinityGroup: required
     *   addressPrefix: required (e.g., 172.16.0.0/16)
     *   subnetName: required
     *   subnetAddressPrefix: required (e.g., 172.16.0.0/24)
     *
     * @exception
     *   Duplicate example: <Error xmlns="http://schemas.microsoft.com/windowsazure" xmlns:i="http://www.w3.org/2001/XMLSchema-instance"><Code>BadRequest</Code><Message>Multiple virtual network sites specified with the same name 'mynew123'.</Message></Error>
     */
    def createVirtualNetwork(Map args) {
        // There is no call to create a new virtual network, so we need to PUT the entire
        // virtual network configuration.

        // First, retrieve the current config.
        // Kill everything before < as the extra characters cause XML parsing errors.
        def currentConfig = getVirtualNetworkConfiguration(XML).replaceFirst('^[^<]*', '')

        // println "current config=" + currentConfig

        def root = new XmlParser().parseText(currentConfig)

        // Construct the new virtual network XML node.
        def newNodeContent = {
            VirtualNetworkSite(name: args.name, AffinityGroup: args.affinityGroup) {
                Subnets {
                    Subnet(name: args.subnetName) {
                        AddressPrefix(args.subnetAddressPrefix)
                    }
                }
                AddressSpace {
                    AddressPrefix(args.addressPrefix)
                }
            }
        }

        // Inject the new virtual network XML to the current config.
        def Node newNode = root.VirtualNetworkConfiguration.VirtualNetworkSites[0].appendNode("")
        newNode.replaceNode(newNodeContent)

        def writer = new StringWriter();
        def nodePrinter = new XmlNodePrinter(new PrintWriter(writer))
        // Must preserve whitespace.  Otherwise the printer adds extraneous spaces and the server barfs on it.
        nodePrinter.preserveWhitespace = true
        nodePrinter.print(root)
        def requestXml = writer.toString()

        return put(
                path: "services/networking/media",
                requestContentType: TEXT,
                body: requestXml
        )
    }

    /**
     * Deletes a virtual network.
     * @param args
     *   name: the name of the virtual network to delete.
     */
    def deleteVirtualNetwork(Map args) {
        // There is no call to delete a new virtual network, so we need to PUT the entire
        // virtual network configuration.

        // First, retrieve the current config.
        // Kill everything before < as the extra characters cause XML parsing errors.
        def currentConfig = getVirtualNetworkConfiguration(XML).replaceFirst('^[^<]*', '')

        // println "current config=" + currentConfig

        def root = new XmlParser().parseText(currentConfig)

        // Remove the virtual network XML from the current config.
        def Node nodeToDelete = root.VirtualNetworkConfiguration.VirtualNetworkSites.VirtualNetworkSite.find { it.@name == args.name }
        nodeToDelete.parent().remove(nodeToDelete)

        def writer = new StringWriter();
        def nodePrinter = new XmlNodePrinter(new PrintWriter(writer))
        // Must preserve whitespace.  Otherwise the printer adds extraneous spaces and the server barfs on it.
        nodePrinter.preserveWhitespace = true
        nodePrinter.print(root)
        def requestXml = writer.toString()

        return put(
                path: "services/networking/media",
                requestContentType: TEXT,
                body: requestXml
        )
    }

    /**
     * Creates a storage account.
     * Note that this call is asynchronous.
     * If there are no validation errors, the server returns 202 (Accepted).
     * The request status can be checked via getRequestStatus(requestId).
     *
     * @param args
     *   name: Required. A name for the storage account that is unique within Azure. Storage account names must be between
     *   3 and 24 characters in length and use numbers and lower-case letters only.
     *   This name is the DNS prefix name and can be used to access blobs, queues, and tables in the storage account.
     *   For example: http://ServiceName.blob.core.windows.net/mycontainer/
     */
    def createStorageAccount(Map args) {
        return post(
                path: "services/storageservices",
                requestContentType: XML,
                body: {
                    mkp.xmlDeclaration()
                    CreateStorageServiceInput(xmlns: "http://schemas.microsoft.com/windowsazure") {
                        ServiceName(args.name)
                        Description(args.description)
                        Label(args.name.bytes.encodeBase64().toString())
                        AffinityGroup(args.affinityGroup)
                    }
                }
        )
    }

    /**
     * Deletes a storage account.
     * Note that this call is asynchronous.
     * If there are no validation errors, the server returns 202 (Accepted).
     * The request status can be checked via getRequestStatus(requestId).
     *
     * @param args
     *   name: the name of the storage account to delete.
     */
    def deleteStorageAccount(Map args) {
        return delete(path: String.format('services/storageservices/%s', args.name))
    }

    /**
     * Gets all storage accounts under the subscription.
     * @param format: JSON or XML
     */
    def getStorageAccounts(ContentType format = ContentType.JSON) {
        return get(path: "services/storageservices", format: format)
    }

    /**
     * Gets all available OS images that can be used to create disks for new VMs.
     * @param format: JSON or XML
     */
    def getOsImages(ContentType format = ContentType.JSON) {
        return get(path: "services/images", format: format)
    }

    /**
     * Gets all disks under the subscription.
     * @param format: JSON or XML
     */
    def getDisks(ContentType format = ContentType.JSON) {
        return get(path: "services/disks", format: format)
    }

    /**
     * Deletes a disk.
     *
     * @param args
     *     name: the name of the disk to delete.
     */
    def deleteDisk(Map args) {
        return delete(path: String.format('services/disks/%s', args.name))
    }

    /**
     * Gets all image under the subscription.
     * @param format: JSON or XML
     */
    def getVmImages(ContentType format = ContentType.JSON) {
        return get(path: "services/vmimages", format: format)
    }

    /**
     * Gets all cloud services under the subscription.
     * @param format: JSON or XML
     */
    def getCloudServices(ContentType format = ContentType.JSON) {
        return get(path: "services/hostedservices", format: format)
    }

    /**
     * Creates a cloud service.
     * Before creating a VM, you need to create a cloud service.
     * @param
     *   name: name of the cloud service to create
     *   description
     *   affinity group: affinity group to which this cloud service will belong
     */
    def createCloudService(Map args) {
        return post(
            path: "services/hostedservices",
            requestContentType: XML,
            body: {
                mkp.xmlDeclaration()
                CreateHostedService(xmlns: "http://schemas.microsoft.com/windowsazure") {
                    ServiceName(args.name)
                    Label(args.name.bytes.encodeBase64().toString())
                    Description(args.description)
                    AffinityGroup(args.affinityGroup)
                }
            }
        )
    }

    /**
     * Deletes a cloud service.
     * Note that this call is asynchronous.
     * If there are no validation errors, the server returns 202 (Accepted).
     * The request status can be checked via getRequestStatus(requestId).
     *
     * @param args
     *   name: the name of the cloud service to delete
     */
    def deleteCloudService(Map args) {
        return delete(path: String.format('services/hostedservices/%s', args.name))
    }

    /**
     * Creates a virtual machine.
     * Note that this call is asynchronous.
     * If there are no validation errors, the server returns 202 (Accepted).
     * The request status can be checked via getRequestStatus(requestId).
     *
     * @param args
     *   name: the name of the virtual machine to create
     *   deploymentSlot: "production" or "staging"
     *   label
     *   virtualNetworkName
     *   imageName: the name of the image from which the disk will be created.  Pick one from the output of getOsImages().
     *   imageStoreUri: the URI under the blob storage where the disk created from image will be stored (path to a new file)
     *   hostname
     *   username: username of the account that gets SSH access
     *   password: password of the accounts that gets SSH access
     *   disableSshPasswordAuthentication
     *   subnetName
     *   virtualNetworkName
     *   vmType: specifies the size of the VM.  Can be one of "ExtraSmall", "Small", "Medium", "Large", or "ExtraLarge".
     */
    def createVirtualMachine(Map args) {
        return post(
                path: String.format("services/hostedservices/%s/deployments", args.name),
                requestContentType: 'application/atom+xml',
                body: {
                    Deployment(xmlns: "http://schemas.microsoft.com/windowsazure", "xmlns:i": "http://www.w3.org/2001/XMLSchema-instance") {
                        Name(args.name)
                        DeploymentSlot(args.deploymentSlot)
                        Label(args.label)
                        RoleList {
                            Role {
                                RoleName(args.name)
                                RoleType('PersistentVMRole')
                                ConfigurationSets {
                                    ConfigurationSet {
                                        ConfigurationSetType('LinuxProvisioningConfiguration')
                                        HostName(args.hostname)
                                        UserName(args.username)
                                        UserPassword(args.password)
                                        DisableSshPasswordAuthentication(args.disableSshPasswordAuthentication)
                                        /*
                                        SSH {
                                            PublicKeys {
                                                PublicKey {
                                                    FingerPrint(args.sshPublicKeyFingerPrint)
                                                    Path(args.sshPublicKeyPath)
                                                }
                                            }
                                            KeyPairs {
                                                KeyPair {
                                                    FingerPrint(args.sshKeyPairFingerPrint)
                                                    Path(args.sshKeyPairPath)
                                                }
                                            }
                                        }
                                        */
                                    }
                                    ConfigurationSet {
                                        ConfigurationSetType('NetworkConfiguration')
                                        InputEndpoints {
                                            InputEndpoint {
                                                LocalPort(22)
                                                Name('ssh')
                                                Port(22)
                                                Protocol('tcp')
                                            }
                                        }
                                        SubnetNames {
                                            SubnetName(args.subnetName)
                                        }
                                    }

                                }
                                OSVirtualHardDisk {
                                    MediaLink(args.imageStoreUri)
                                    SourceImageName(args.imageName)
                                }
                                RoleSize(args.vmType)
                            }
                        }
                        VirtualNetworkName(args.virtualNetworkName)
                    }
                }
        )
    }

    /**
     * Deletes a virtual machine.
     * Note that this call is asynchronous.
     * If there are no validation errors, the server returns 202 (Accepted).
     * The request status can be checked via getRequestStatus(requestId).
     *
     * @param args
     *   name: the name of the virtual machine to delete
     */
    def deleteVirtualMachine(Map args) {
        return delete(path: String.format('services/hostedservices/%s/deployments/%s', args.name, args.name))
    }

    static String convert(String response) throws XMLStreamException, IOException {
        try {
            String xmlHeader= "xmlns=\"http://schemas.microsoft.com/windowsazure\" xmlns:i=\"http://www.w3.org/2001/XMLSchema-instance\""
            HierarchicalStreamReader sourceReader = new XppReader(new StringReader(response.toString().replaceAll(xmlHeader, "")))

            StringWriter buffer = new StringWriter()
            JettisonMappedXmlDriver jettisonDriver = new JettisonMappedXmlDriver()
            jettisonDriver.createWriter(buffer)
            HierarchicalStreamWriter destinationWriter = jettisonDriver.createWriter(buffer)

            HierarchicalStreamCopier copier = new HierarchicalStreamCopier()
            copier.copy(sourceReader, destinationWriter)
            return buffer.toString()
        } catch (Exception ex) {
            println(ex.getMessage())
            return null
        }

    }
}
