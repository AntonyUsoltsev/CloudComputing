import java.io.*;

public class SerializeArgs {
    public static void main(String[] args) throws Exception {
        Object[] arguments = {32, 44};

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(arguments);
        oos.close();

        String base64 = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
        System.out.println(base64);
    }
}