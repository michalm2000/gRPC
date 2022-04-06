import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Vector;

public class GRPCClient {
    public static void main(String[] args) {
        String address = "localhost";
        int port = 50001;
        System.out.println("Running grpc client...");
//        ManagedChannel channel = ManagedChannelBuilder.forAddress(address, port).usePlaintext().build();
//        GrpcServiceGrpc.GrpcServiceBlockingStub stub = GrpcServiceGrpc.newBlockingStub(channel);
//        GrpcRequest request = GrpcRequest.newBuilder().setName("me").setAge(21).build();
//        GrpcResponse response = stub.grpcProcedure(request);
//        System.out.println(response);
//        channel.shutdown();


        Scanner sc = new Scanner(System.in);
        String command= "";

        while(!command.equals("exit")) {
            command = sc.nextLine();
            ManagedChannel channel = ManagedChannelBuilder.forAddress(address, port).usePlaintext().build();
            GrpcServiceGrpc.GrpcServiceBlockingStub stub = GrpcServiceGrpc.newBlockingStub(channel);

            System.out.println(commandInterpreter(command, stub));

            channel.shutdown();
        }
    }

    public static Object commandInterpreter(String command, GrpcServiceGrpc.GrpcServiceBlockingStub stub){
        try {
            if(command.isEmpty()){
                return "";
            }

            Vector<String> commandSplitted = new Vector<>(List.of(command.split("\\s+")));
            if (commandSplitted.firstElement().equals("exit")) {
                return "exiting...";
            }
            String commandName = commandSplitted.remove(0);
            switch (commandName.toLowerCase(Locale.ROOT)){
                case "grpcprocedure":
                    if (commandSplitted.size() != 2) return "Invalid number of arguments";
                    try {
                        GrpcRequest request = GrpcRequest.newBuilder().setName(commandSplitted.get(0)).setAge(Integer.parseInt(commandSplitted.get(1))).build();
                        return stub.grpcProcedure(request);
                    } catch (NumberFormatException e){
                        System.out.println("Invalid arguments");
                    }
                case "savecd":
                    if (commandSplitted.size() != 4) return "Invalid number of arguments";
                    try {
                        CD cd = CD.newBuilder().setAuthor(commandSplitted.get(0)).setTitle(commandSplitted.get(1))
                                .setSongsCount(Integer.parseInt(commandSplitted.get(2)))
                                .setLabel(commandSplitted.get(3)).build();
                        return stub.saveCD(cd);
                    } catch (NumberFormatException e){
                        System.out.println("Invalid arguments");
                    }

                case "getcds":
                    CDs cds = stub.getCDs(Empty.newBuilder().build());
                    return cds.getCdsList().size() > 0 ? cds : "No cds found";

                case "getcd":
                    if (commandSplitted.size() != 1) return "Invalid number of arguments";
                    GrpcResponse response = GrpcResponse.newBuilder().setMessage(commandSplitted.get(0)).build();
                    var res = stub.getCDbyTitle(response);
                    return res.getTitle()!="" ?  res : "No CD found";

                default:
                    return "Command does not exist";

            }
        } catch (Exception exception){
            System.err.println("gRPC client: " + exception);
        }
        return ""

    ;}
}
