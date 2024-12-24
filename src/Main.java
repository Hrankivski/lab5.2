import javax.json.*;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class Flight {
    String file;
    public int from;
    public int to;
    public List<Seat> seats = new ArrayList<>();

    public static class Seat {
        public double price;
        public String status;
    }
}

public class Main {
    private static final String[] flightFiles = {
            "src/flights1.json",
            "src/flights2.json",
            "src/flights3.json",
            "src/flights4.json"
    };
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        List<Flight> allFlights = loadFlights();

        String userInput;
        do {
            System.out.println("Available flights:");
            allFlights.stream().map(f -> f.from + "-" + f.to).distinct().forEach(System.out::println);

            System.out.println("Enter your flight choice (e.g., 1-2):");
            String chosenFlight = scanner.nextLine();
            String[] parts = chosenFlight.split("-");
            int from = Integer.parseInt(parts[0]);
            int to = Integer.parseInt(parts[1]);

            Optional<Flight.Seat> bestSeat = findBestSeat(allFlights, from, to);

            bestSeat.ifPresent(seat -> {
                System.out.println("Best price for your flight is $" + seat.price + ". Do you want to book it? (yes/no)");
                String response = scanner.nextLine();
                if ("yes".equalsIgnoreCase(response)) {
                    System.out.println("Enter your name:");
                    String name = scanner.nextLine();
                    System.out.println("Write 'pay' to confirm payment:");
                    String pay = scanner.nextLine();
                    if ("pay".equalsIgnoreCase(pay)) {
                        CompletableFuture<Void> updateTask = updateSeatAvailability(allFlights, from, to, seat);
                        updateTask.join();  // Ensure the task completes
                        System.out.println("Ticket booked successfully for " + name + ".");
                    }
                }
            });

            System.out.println("Do you want to perform another booking? (yes/no)");
            userInput = scanner.nextLine();
        } while ("yes".equalsIgnoreCase(userInput));

        executor.shutdown();
    }

    private static List<Flight> loadFlights() throws Exception {
        List<Flight> flights = new ArrayList<>();
        for (String file : flightFiles) {
            try (InputStream fis = new FileInputStream(file);
                 JsonReader jsonReader = Json.createReader(fis)) {
                JsonObject rootObject = jsonReader.readObject();
                JsonArray flightsArray = rootObject.getJsonArray("flights");
                for (JsonObject flightObj : flightsArray.getValuesAs(JsonObject.class)) {
                    Flight flight = new Flight();
                    flight.file = file;
                    flight.from = flightObj.getInt("from");
                    flight.to = flightObj.getInt("to");
                    JsonArray seatsArray = flightObj.getJsonArray("seats");
                    for (JsonObject seatObj : seatsArray.getValuesAs(JsonObject.class)) {
                        Flight.Seat seat = new Flight.Seat();
                        seat.price = seatObj.getJsonNumber("price").doubleValue();
                        seat.status = seatObj.getString("status");
                        flight.seats.add(seat);
                    }
                    flights.add(flight);
                }
            }
        }
        return flights;
    }

    private static Optional<Flight.Seat> findBestSeat(List<Flight> flights, int from, int to) {
        return flights.stream()
                .filter(f -> f.from == from && f.to == to)
                .flatMap(f -> f.seats.stream())
                .filter(s -> "free".equals(s.status))
                .min(Comparator.comparingDouble(s -> s.price));
    }

    private static CompletableFuture<Void> updateSeatAvailability(List<Flight> flights, int from, int to, Flight.Seat chosenSeat) {
        return CompletableFuture.runAsync(() -> {
            flights.stream()
                    .filter(f -> f.from == from && f.to == to)
                    .forEach(f -> {
                        f.seats.stream()
                                .filter(s -> s.price == chosenSeat.price && "free".equals(s.status))
                                .forEach(s -> {
                                    s.status = "booked";
                                    saveFlightUpdates(Collections.singletonList(f));  // Save to JSON file
                                });
                    });
        }, executor);
    }

    private static void saveFlightUpdates(List<Flight> flights) {
        for (Flight flight : flights) {
            try (FileOutputStream fos = new FileOutputStream(flight.file);
                 JsonWriter jsonWriter = Json.createWriter(fos)) {
                JsonObjectBuilder rootBuilder = Json.createObjectBuilder();
                JsonArrayBuilder flightsArrayBuilder = Json.createArrayBuilder();

                for (Flight f : flights) {
                    JsonObjectBuilder flightBuilder = Json.createObjectBuilder()
                            .add("from", f.from)
                            .add("to", f.to);

                    JsonArrayBuilder seatsArrayBuilder = Json.createArrayBuilder();
                    for (Flight.Seat s : f.seats) {
                        seatsArrayBuilder.add(Json.createObjectBuilder()
                                .add("price", s.price)
                                .add("status", s.status));
                    }
                    flightBuilder.add("seats", seatsArrayBuilder);
                    flightsArrayBuilder.add(flightBuilder);
                }

                rootBuilder.add("flights", flightsArrayBuilder);
                JsonObject root = rootBuilder.build();
                jsonWriter.writeObject(root);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
