import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

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
            var nonBlockingStub = GrpcServiceGrpc.newStub(channel);
            System.out.println(commandInterpreter(command, stub, nonBlockingStub));
            channel.shutdown();

        }
    }

    public static Object commandInterpreter(String command, GrpcServiceGrpc.GrpcServiceBlockingStub stub,
                                            GrpcServiceGrpc.GrpcServiceStub nonBlockingStub){
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

                case "getcdsasync":
                    StreamObserver<CDs> responseCDObserver = new StreamObserver<CDs>() {
                        @Override
                        public void onNext(CDs cDs) {
                            cDs.getCdsList().forEach(System.out::println);
                        }

                        @Override
                        public void onError(Throwable throwable) {

                        }

                        @Override
                        public void onCompleted() {

                        }
                    };
                    nonBlockingStub.getCDsAsync(Empty.newBuilder().build(),responseCDObserver);
                    break;

                case "getcd":
                    if (commandSplitted.size() != 1) return "Invalid number of arguments";
                    GrpcResponse response = GrpcResponse.newBuilder().setMessage(commandSplitted.get(0)).build();
                    var res = stub.getCDbyTitle(response);
                    return !res.getTitle().equals("") ?  res : "No CD found";

                case "getsongtext":
                    if (commandSplitted.size() != 1) return "Invalid number of arguments";
                    try {
                        int numberOfRequests = Integer.parseInt(commandSplitted.get(0));
                        List<SongLineGetter> requests = new ArrayList<>();
                        for (int i = 0; i < numberOfRequests; i++){
                            requests.add(SongLineGetter.newBuilder().setLineNumber(i+1).build());
                        }


                        StreamObserver<SongLine> responseObserver = new StreamObserver<SongLine>() {
                            @Override
                            public void onNext(SongLine songLine) {
                                System.out.println(songLine.getLineNumber() + " / " + songLine.getAllLines());
                                System.out.println(songLine.getLine());
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                System.out.println("Number of song verses exceeded");
                            }

                            @Override
                            public void onCompleted() {

                            }
                        };

                        StreamObserver<SongLineGetter> requestObserver = nonBlockingStub.getSongText(responseObserver);
                        try {
                            for (SongLineGetter slg: requests) {
                                requestObserver.onNext(slg);
                                Thread.sleep(200);

                            }


                        } catch (RuntimeException e) {
                            requestObserver.onError(e);
                            throw e;
                        }
                        requestObserver.onCompleted();
                    } catch (NumberFormatException e){
                        System.out.println("Invalid arguments");
                    }
                    break;

                case "upload":
                    StreamObserver<FileUploadResponse> responseObserver3 = new StreamObserver<FileUploadResponse>() {
                        @Override
                        public void onNext(FileUploadResponse fileUploadResponse) {
                            System.out.println(
                                    "File upload status :: " + fileUploadResponse.getStatus()
                            );
                        }

                        @Override
                        public void onError(Throwable throwable) {

                        }

                        @Override
                        public void onCompleted() {

                        }
                    };
                    StreamObserver<FileUploadRequest> requestObserver3 = nonBlockingStub.upload(responseObserver3);
                    Path path = Paths.get("C:\\Users\\michaldes\\IdeaProjects\\gRPC\\gRPCClient\\src\\main\\resources\\Images\\hour.jpg");
                    FileUploadRequest metadata = FileUploadRequest.newBuilder()
                            .setMetadata(
                                    MetaData.newBuilder().setName("file").setType("jpg").build()
                            ).build();
                    requestObserver3.onNext(metadata);

                    InputStream inputStream = Files.newInputStream(path);
                    byte[] bytes = new byte[4096];
                    int size;
                    while ((size = inputStream.read(bytes)) > 0){
                        FileUploadRequest uploadRequest = FileUploadRequest.newBuilder()
                                .setFile(File.newBuilder().setContent(ByteString.copyFrom(bytes, 0, size)).build())
                                .build();
                        requestObserver3.onNext(uploadRequest);
                    }
                    inputStream.close();
                    requestObserver3.onCompleted();
                    break;

                default:
                    return "Command does not exist";

            }
        } catch (Exception exception){
            System.err.println("gRPC client: " + exception);
        }
        return ""

    ;}
}
