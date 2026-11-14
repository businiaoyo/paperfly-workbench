package cn.paperfly.workbench.activation;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** One application-private identity, atomically stored with no backup or external export. */
final class AdbIdentity {
    final PrivateKey key;
    final X509Certificate certificate;
    private AdbIdentity(PrivateKey key,X509Certificate certificate){this.key=key;this.certificate=certificate;}
    static synchronized AdbIdentity load(Context context)throws Exception{
        AtomicFile file=new AtomicFile(new File(context.getNoBackupFilesDir(),"wireless-adb-identity.bin"));
        if(file.getBaseFile().exists()){
            try(DataInputStream in=new DataInputStream(file.openRead())){
                if(in.readInt()!=1)throw new IOException("无线激活密钥格式不支持");
                byte[] encodedKey=read(in),encodedCertificate=read(in);
                try{return new AdbIdentity(KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encodedKey)),
                        (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(encodedCertificate)));}
                finally{java.util.Arrays.fill(encodedKey,(byte)0);}
            }
        }
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048,new SecureRandom());
        java.security.KeyPair pair=generator.generateKeyPair();long now=System.currentTimeMillis();
        X500Name name=new X500Name("CN=OpenWorkbench");
        byte[] cert=new JcaX509v3CertificateBuilder(name,new BigInteger(128,new SecureRandom()).setBit(0),
                new Date(now-86400000L),new Date(now+10L*365*86400000),name,pair.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())).getEncoded();
        byte[] privateBytes=pair.getPrivate().getEncoded();FileOutputStream out=null;
        try{
            out=file.startWrite();DataOutputStream data=new DataOutputStream(out);data.writeInt(1);data.writeInt(privateBytes.length);data.write(privateBytes);
            data.writeInt(cert.length);data.write(cert);data.flush();file.finishWrite(out);out=null;
        }finally{if(out!=null)file.failWrite(out);java.util.Arrays.fill(privateBytes,(byte)0);}
        return new AdbIdentity(pair.getPrivate(),(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(cert)));
    }
    private static byte[] read(DataInputStream in)throws IOException{
        int size=in.readInt();if(size<1||size>16384)throw new IOException("无线激活密钥文件损坏，请保留文件并重新安装修复版本");
        byte[] bytes=new byte[size];in.readFully(bytes);return bytes;
    }
}
