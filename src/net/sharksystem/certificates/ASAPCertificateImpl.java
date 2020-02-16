package net.sharksystem.certificates;

import net.sharksystem.asap.util.Log;

import java.io.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Calendar;

public class ASAPCertificateImpl implements ASAPCertificate {
    public static final int DEFAULT_CERTIFICATE_VALIDITY_IN_YEARS = 1;
    public static final String DEFAULT_SIGNATURE_METHOD = "SHA256withRSA";

    private PublicKey publicKey;
    private CharSequence ownerName;
    private int ownerID;
    private CharSequence signerName;
    private int signerID;
    private byte[] signatureBytes;
    private ASAPStorageAddress asapStorageAddress;
    private Calendar validSince;
    private Calendar validUntil;

    /**
     * Create fresh certificate for owner and sign it now with signers private key.
     * @param signerID
     * @param signerName
     * @param privateKey
     * @param ownerID
     * @param ownerName
     * @param publicKey
     * @return
     * @throws SignatureException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws IOException
     */
    public static ASAPCertificateImpl produceCertificate(int signerID,
                               CharSequence signerName,
                               PrivateKey privateKey,
                               int ownerID, CharSequence ownerName,
                               PublicKey publicKey) throws SignatureException,
            NoSuchAlgorithmException, InvalidKeyException, IOException {

        ASAPCertificateImpl asapCertificate = new ASAPCertificateImpl(signerID, signerName, ownerID, ownerName, publicKey);
        asapCertificate.sign(privateKey);

        return asapCertificate;
    }

    private ASAPCertificateImpl(int signerID,
                                CharSequence signerName,
                                int ownerID, CharSequence ownerName,
                                PublicKey publicKey) throws IOException,
            NoSuchAlgorithmException, InvalidKeyException, SignatureException {

        this.signerID = signerID;
        this.signerName = signerName;
        this.ownerID = ownerID;
        this.ownerName = ownerName;
        this.publicKey = publicKey;

        this.validSince = Calendar.getInstance();
        this.validUntil = Calendar.getInstance();
        this.validUntil.add(Calendar.YEAR, DEFAULT_CERTIFICATE_VALIDITY_IN_YEARS);
    }

    private void sign(PrivateKey privateKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        // create signature
        Signature signature = Signature.getInstance(DEFAULT_SIGNATURE_METHOD);
        signature.initSign(privateKey, new SecureRandom()); // TODO: should use a seed
        signature.update(this.getAnythingButSignatur());
        this.signatureBytes = signature.sign();
    }

    public static ASAPCertificateImpl produceCertificateFromStorage(
            byte[] serializedMessage, ASAPStorageAddress asapStorageAddress)
            throws IOException, NoSuchAlgorithmException, InvalidKeyException, SignatureException, InvalidKeySpecException {

        ByteArrayInputStream bais = new ByteArrayInputStream(serializedMessage);
        DataInputStream dis = new DataInputStream(bais);

        int signerID = dis.readInt();
        String signerName = dis.readUTF();
        int ownerID = dis.readInt();
        String ownerName = dis.readUTF();
        long validSinceLong = dis.readLong();
        long validUntilLong = dis.readLong();

        String algorithm = dis.readUTF();
        int length = dis.readInt();
        byte[] pubKeyBytes = new byte[length];
        dis.read(pubKeyBytes);

        // decode public key
        KeyFactory keyFactory = null;
        keyFactory = KeyFactory.getInstance(algorithm);
        PublicKey pubKey = keyFactory.generatePublic(new X509EncodedKeySpec(pubKeyBytes));

        length = dis.readInt();
        byte[] signaturBytes = new byte[length];
        dis.read(signaturBytes);

        ASAPCertificateImpl asapCertificate = new ASAPCertificateImpl(signerID, signerName, ownerID, ownerName, pubKey);
        asapCertificate.signatureBytes = signaturBytes;
        asapCertificate.asapStorageAddress = asapStorageAddress;

        return asapCertificate;
    }

    private byte[] getAnythingButSignatur() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream daos = new DataOutputStream(baos);
        this.fillWithAnythingButSignature(daos);
        return baos.toByteArray();
    }

    private void fillWithAnythingButSignature(DataOutputStream dos) {
        // create byte array that is to be signed
        try {
            dos.writeInt(this.signerID);
            dos.writeUTF(this.signerName.toString());
            dos.writeInt(this.ownerID);
            dos.writeUTF(this.getOwnerName().toString());

            dos.writeLong(this.validSince.getTimeInMillis());
            dos.writeLong(this.validUntil.getTimeInMillis());

            // public key serialization
            dos.writeUTF(this.publicKey.getAlgorithm());
            byte[] pubKeyBytes = this.publicKey.getEncoded();

            dos.writeInt(pubKeyBytes.length);
            dos.write(pubKeyBytes);
        }
        catch (IOException ioe) {
            // cannot happen - really
        }
    }

    public byte[] asBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream daos = new DataOutputStream(baos);

        this.fillWithAnythingButSignature(daos);

        try {
            daos.writeInt(this.signatureBytes.length);
            daos.write(this.signatureBytes);
        } catch (IOException e) {
            // cannot happen - really
        }

        return baos.toByteArray();
    }

    @Override
    public boolean verify(PublicKey publicKeyIssuer) throws NoSuchAlgorithmException {
        Signature signature = Signature.getInstance(DEFAULT_SIGNATURE_METHOD);

        try {
            signature.initVerify(publicKeyIssuer);
            signature.update(this.getAnythingButSignatur());
            return signature.verify(this.signatureBytes);
        }
        catch(Exception e) {
            return false;
        }
    }

    @Override
    public ASAPStorageAddress getASAPStorageAddress() {
        return this.asapStorageAddress;
    }

    @Override
    public int getOwnerID() { return this.ownerID; }

    @Override
    public CharSequence getOwnerName() { return this.ownerName; }

    @Override
    public CharSequence getSignerName() {  return this.signerName;  }

    @Override
    public int getSignerID() { return this.signerID; }

    @Override
    public Calendar getValidSince() { return this.validSince; }

    @Override
    public Calendar getValidUntil() { return this.validUntil; }
}
