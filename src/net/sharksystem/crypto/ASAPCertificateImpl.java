package net.sharksystem.crypto;

import net.sharksystem.asap.util.Log;

import java.io.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.Calendar;

public class ASAPCertificateImpl implements ASAPCertificate {
    public static final int DEFAULT_CERTIFICATE_VALIDITY_IN_YEARS = 1;
    public static final String DEFAULT_SIGNATURE_METHOD = "SHA256withRSA";

    private PublicKey publicKey;
    private CharSequence ownerName;
    private CharSequence ownerID;
    private CharSequence signerName;
    private CharSequence signerID;
    private byte[] signatureBytes;
    private ASAPStorageAddress asapStorageAddress;
    private long validSince;
    private long validUntil;
    private String signingAlgorithm;

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
    public static ASAPCertificateImpl produceCertificate(
            CharSequence signerID,
            CharSequence signerName,
            PrivateKey privateKey,
            CharSequence ownerID, CharSequence ownerName,
            PublicKey publicKey,
            long validSince,
            CharSequence signingAlgorithm)
                throws SignatureException, NoSuchAlgorithmException, InvalidKeyException {

        long now = System.currentTimeMillis();
        if(validSince > now) {
            Log.writeLog(ASAPCertificateImpl.class, "valid since cannot be in future - set to now");
            validSince = now;
        }

        Calendar since = Calendar.getInstance();
        since.setTimeInMillis(validSince);

        Calendar until = Calendar.getInstance();
        until.setTimeInMillis(validSince);
        until.add(Calendar.YEAR, DEFAULT_CERTIFICATE_VALIDITY_IN_YEARS);

        Log.writeLog(ASAPCertificateImpl.class, "signerID: " + signerID);
        Log.writeLog(ASAPCertificateImpl.class, "signerName: " + signerName);
        Log.writeLog(ASAPCertificateImpl.class, "privateKey: " + privateKey);
        Log.writeLog(ASAPCertificateImpl.class, "ownerID: " + ownerID);
        Log.writeLog(ASAPCertificateImpl.class, "ownerName: " + ownerName);
        Log.writeLog(ASAPCertificateImpl.class, "publicKey: " + publicKey);

        ASAPCertificateImpl asapCertificate = new ASAPCertificateImpl(
                signerID, signerName, ownerID, ownerName, publicKey, validSince,
                until.getTimeInMillis(), signingAlgorithm);

        asapCertificate.sign(privateKey);

        return asapCertificate;
    }

    private ASAPCertificateImpl(CharSequence signerID,
                                CharSequence signerName,
                                CharSequence ownerID, CharSequence ownerName,
                                PublicKey publicKey, long validSince, long validUntil,
                                CharSequence signingAlgorithm) {
        this.signerID = signerID;
        this.signerName = signerName;
        this.ownerID = ownerID;
        this.ownerName = ownerName;
        this.publicKey = publicKey;

        this.validSince = validSince;
        this.validUntil = validUntil;

        this.signingAlgorithm = signingAlgorithm.toString();
    }

    void setASAPStorageAddress(ASAPStorageAddress asapStorageAddress) {
        this.asapStorageAddress = asapStorageAddress;
    }

    private void sign(PrivateKey privateKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        // create signature
//        Signature signature = Signature.getInstance(DEFAULT_SIGNATURE_METHOD);
        Signature signature = Signature.getInstance(this.signingAlgorithm);
        Log.writeLog(this, "got signature object: " + signature);
        signature.initSign(privateKey, new SecureRandom()); // TODO: should use a seed
        Log.writeLog(this, "initialized signature object for signing");
        signature.update(this.getAnythingButSignatur());
        Log.writeLog(this, "updated signature object");
        this.signatureBytes = signature.sign();
        Log.writeLog(this, "got signature");
    }

    @Override
    public boolean verify(PublicKey publicKeyIssuer) throws NoSuchAlgorithmException {
//        Signature signature = Signature.getInstance(DEFAULT_SIGNATURE_METHOD);
        Signature signature = Signature.getInstance(this.signingAlgorithm);
        Log.writeLog(this, "got signature object: " + signature);

        try {
            signature.initVerify(publicKeyIssuer);
            Log.writeLog(this, "got signature object for verifying: " + signature);
            signature.update(this.getAnythingButSignatur());
            Log.writeLog(this, "updated signature object");
            return signature.verify(this.signatureBytes);
        }
        catch(Exception e) {
            Log.writeLogErr(this, "exception during verification:  " + e.getLocalizedMessage());
            return false;
        }
    }

    public static ASAPCertificateImpl produceCertificateFromBytes(
            byte[] serializedMessage)
                throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        ByteArrayInputStream bais = new ByteArrayInputStream(serializedMessage);
        DataInputStream dis = new DataInputStream(bais);

        String signerID = dis.readUTF();
        String signerName = dis.readUTF();
        String ownerID = dis.readUTF();
        String ownerName = dis.readUTF();
        long validSince = dis.readLong();
        long validUntil = dis.readLong();
        String signingAlgorithm = dis.readUTF();

        // read public key
        PublicKey pubKey = KeyHelper.readPublicKeyFromStream(dis);

        int length = dis.readInt();
        byte[] signatureBytes = new byte[length];
        dis.read(signatureBytes);

        ASAPCertificateImpl asapCertificate = new ASAPCertificateImpl(
                signerID, signerName, ownerID, ownerName, pubKey, validSince, validUntil, signingAlgorithm);

        asapCertificate.signatureBytes = signatureBytes;

        return asapCertificate;
    }

    public static ASAPCertificateImpl produceCertificateFromStorage(
            byte[] serializedMessage, ASAPStorageAddress asapStorageAddress)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        ASAPCertificateImpl asapCertificate = ASAPCertificateImpl.produceCertificateFromBytes(serializedMessage);

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
            dos.writeUTF(this.signerID.toString());
            dos.writeUTF(this.signerName.toString());
            dos.writeUTF(this.ownerID.toString());
            dos.writeUTF(this.getOwnerName().toString());

            dos.writeLong(this.validSince);
            dos.writeLong(this.validUntil);
            dos.writeUTF(this.signingAlgorithm);

            // public key serialization
            KeyHelper.writePublicKeyToStream(this.publicKey, dos);

            /*
            dos.writeUTF(this.publicKey.getAlgorithm());
            byte[] pubKeyBytes = this.publicKey.getEncoded();

            dos.writeInt(pubKeyBytes.length);
            dos.write(pubKeyBytes);
             */
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
            Log.writeLogErr(this, "could not happen but did while serializing a certificate (ignored): "
                    + e.getLocalizedMessage());
        }

        return baos.toByteArray();
    }

    @Override
    public ASAPStorageAddress getASAPStorageAddress() {
        return this.asapStorageAddress;
    }

    @Override
    public CharSequence getOwnerID() { return this.ownerID; }

    @Override
    public CharSequence getOwnerName() { return this.ownerName; }

    @Override
    public CharSequence getSignerName() {  return this.signerName;  }

    @Override
    public CharSequence getSignerID() { return this.signerID; }

    public static Calendar long2Calendar(long timeInMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeInMillis);
        return calendar;
    }

    @Override
    public Calendar getValidSince() { return long2Calendar(this.validSince); }

    @Override
    public Calendar getValidUntil() { return long2Calendar(this.validUntil); }

    public PublicKey getPublicKey() { return this.publicKey; }
}
