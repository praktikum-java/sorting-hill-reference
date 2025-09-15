package ru.yandex.practicum;

import ru.yandex.practicum.sorting.SortingHandler;
import ru.yandex.practicum.sorting.impl.SortingOperatorImpl;
import ru.yandex.practicum.sorting.impl.SortingReporterImpl;

import java.util.*;

public class SortingHill {
    private static final String SHIFT_STARTED_EVENT = "начало работ";
    private static final String SHIFT_ENDED_EVENT = "окончание работ";
    private static final String WAGON_ARRIVED_EVENT = "вагон на сортировку";
    private static final String LOCO_ARRIVED_EVENT = "подача локомотива";
    private static final String TRAIN_PLANNED_EVENT = "формируйте состав";
    private static final String TRAIN_READY_EVENT = "отправляйте поезд";
    private static final String PREPARE_PATH_EVENT = "готовьте путь";

    private static final String WAGON_EMPTY = "П";
    private static final String WAGON_GRUZ = "Г";
    private static final String WAGON_PASS = "Л";
    private static final String WAGON_GRUZ_OPASN = "О";

    private static final String LOCO_ELECTRO_16 = "ЭВЛ-16";
    private static final String LOCO_ELECTRO_32 = "ЭВЛС-32";
    private static final String LOCO_DIESEL_24 = "ДЛ-24";
    private static final String LOCO_DIESEL_64 = "ТДЛ-64";

    private static final String[] EVENTS_BALANCED = {WAGON_ARRIVED_EVENT, WAGON_ARRIVED_EVENT, WAGON_ARRIVED_EVENT, WAGON_ARRIVED_EVENT, WAGON_ARRIVED_EVENT, WAGON_ARRIVED_EVENT, WAGON_ARRIVED_EVENT, WAGON_ARRIVED_EVENT, WAGON_ARRIVED_EVENT, LOCO_ARRIVED_EVENT, LOCO_ARRIVED_EVENT, LOCO_ARRIVED_EVENT, LOCO_ARRIVED_EVENT, PREPARE_PATH_EVENT, PREPARE_PATH_EVENT, TRAIN_PLANNED_EVENT, TRAIN_READY_EVENT};

    private static final String[] WAGON_TYPES = {WAGON_GRUZ, WAGON_PASS, WAGON_GRUZ_OPASN, WAGON_EMPTY};
    private static final String[] TRAIN_TYPES = {WAGON_GRUZ, WAGON_PASS, WAGON_GRUZ_OPASN};

    private static final List<String> LOCO_TYPES = Arrays.asList(LOCO_ELECTRO_16, LOCO_ELECTRO_32, LOCO_DIESEL_24, LOCO_DIESEL_64);

    private static final Random rand = new Random();

    private final Collection<SortingHandler> handlers = new ArrayList<>();

    private final int numberOfPaths;
    private final Map<Integer, String> assignedPaths = new HashMap<>(); //номер пути -> номер поезда
    private final List<String> wagonBuffer = new LinkedList<>(); //вагоны нераспределенные
    private final Map<String, List<String>> trainsFormed = new HashMap<>(); //номер поезда -> список локомотив + вагоны
    private long trainIndex = 0;

    private SortingHandler hiddenWatchHandler;

    private SortingHill(int numberOfPaths) {
        this.numberOfPaths = numberOfPaths;
    }

    public int getNumberOfPaths() {
        return numberOfPaths;
    }

    public static void main(String[] args) throws InterruptedException {
        SortingHill sortingHill = new SortingHill(Math.max(2, rand.nextInt(16)));
        sortingHill.hiddenWatchHandler = new HiddenWatchImpl(sortingHill);
        try {
            sortingHill.registerHandler(new SortingOperatorImpl());
            sortingHill.registerHandler(new SortingReporterImpl());
        } catch (UnsupportedOperationException e) {
            System.out.println("Нет зарегистрированных операторов, работы не выполняются.");
        }

        long wagonLeft = Math.max(1024, rand.nextLong(4096));
        for (int wi = 0; wi < wagonLeft; wi++) {
            String wagonInfo = String.format("%08d/%s", Math.round(rand.nextDouble() * 99999999.0), WAGON_TYPES[rand.nextInt(WAGON_TYPES.length)]);
            sortingHill.wagonBuffer.add(wagonInfo);
        }
        System.out.printf("Начало рабочей смены, очередь вагонов: %d\n", wagonLeft);
        sortingHill.handleEvent(SHIFT_STARTED_EVENT);
        while (!sortingHill.wagonBuffer.isEmpty()) {
            try {
                String nextEvent = sortingHill.checkEvent(EVENTS_BALANCED[rand.nextInt(EVENTS_BALANCED.length)]);
                if (nextEvent != null) {
                    System.out.printf("Команда дежурного: %s\n", nextEvent);
                    sortingHill.handleEvent(nextEvent);
                    Thread.sleep(rand.nextLong(100));
                }
            } catch (HiddenException he) {
                //ничего не делаем, это для внутреннего пользования
                System.out.println("watch reject: " + he.getMessage()); //TODO закомментировать
            } catch (RuntimeException e) {
                System.err.printf("Произошла ошибка обработки: %s\n", e.getMessage());
            }
        }
        sortingHill.handleEvent(SHIFT_ENDED_EVENT);

        System.out.println("Рабочая смена окончена");
    }

    private void handleEvent(String event) {
        HiddenException watcherException = null;
        switch (event) {
            case SHIFT_STARTED_EVENT:
                hiddenWatchHandler.startShift();
                handlers.forEach(SortingHandler::startShift);
                break;
            case SHIFT_ENDED_EVENT:
                int lastTrainsCount = 0;
                for (Integer path: assignedPaths.keySet()) {
                    String train =  assignedPaths.get(path);
                    List<String> trainContent = trainsFormed.get(train);
                    if(trainContent.size() > 1){
                        lastTrainsCount++;
                    } else {
                        trainsFormed.remove(train);
                        assignedPaths.remove(path);
                    }
                }
                while (lastTrainsCount > 0) {
                    handleEvent(TRAIN_READY_EVENT);
                    lastTrainsCount--;
                }
                hiddenWatchHandler.endShift();
                handlers.forEach(SortingHandler::endShift);
                break;
            case PREPARE_PATH_EVENT:
                try {
                    hiddenWatchHandler.preparePath();
                } catch (HiddenException he) {
                    watcherException = he;
                }
                handlers.forEach(SortingHandler::preparePath);
                break;
            case WAGON_ARRIVED_EVENT:
                String wagonInfo = wagonBuffer.getFirst();
                try {
                    hiddenWatchHandler.handleWagon(wagonInfo);
                    wagonBuffer.removeFirst(); //обработали, просто удаляем
                } catch (HiddenException he) {
                    wagonBuffer.add(wagonBuffer.removeFirst()); //добавляем в конец, чтобы не застопорилась вся очередь
                    watcherException = he;
                }
                handlers.forEach(h -> h.handleWagon(wagonInfo));
                break;
            case LOCO_ARRIVED_EVENT:
                String loco = LOCO_TYPES.get(rand.nextInt(LOCO_TYPES.size()));
                try {
                    hiddenWatchHandler.handleLocomotive(loco);
                } catch (HiddenException he) {
                    watcherException = he;
                }
                handlers.forEach(h -> h.handleLocomotive(loco));
                break;
            case TRAIN_PLANNED_EVENT:
                try {
                    hiddenWatchHandler.allocatePathForTrain();
                } catch (HiddenException he) {
                    watcherException = he;
                }
                handlers.forEach(h -> h.allocatePathForTrain());
                break;
            case TRAIN_READY_EVENT:
                try {
                    hiddenWatchHandler.sendTrain();
                } catch (HiddenException he) {
                    watcherException = he;
                }
                handlers.forEach(h -> h.sendTrain());
                break;
            default:
                throw new RuntimeException("unknown event: " + event);
        }
        if (watcherException != null) {
            throw watcherException;
        }
    }

    private String checkEvent(String candidate) {
        switch (candidate) {
            case PREPARE_PATH_EVENT:
                if (assignedPaths.size() >= numberOfPaths) {
                    return null;
                }
                break;
            case LOCO_ARRIVED_EVENT:
                if (!trainsFormed.containsValue(Collections.emptyList())) {
                    return null;
                }
                break;
            case TRAIN_PLANNED_EVENT:
                if (!assignedPaths.containsValue(null)) {
                    return null;
                }
                break;
            case TRAIN_READY_EVENT:
                String trainReady = null;
                for (String train : trainsFormed.keySet()) {
                    List<String> trainContent = trainsFormed.get(train);
                    if (!trainContent.isEmpty()) {
                        String maybeLoco = trainContent.getFirst();
                        if (!LOCO_TYPES.contains(maybeLoco)) {
                            break;
                        }
                        String[] locoDescr = maybeLoco.split("-");
                        int trainSize = Integer.parseInt(locoDescr[1]);
                        if (trainContent.size() == trainSize + 1 || (trainContent.size() > 1 && wagonBuffer.isEmpty())) {
                            trainReady = train;
                            break;
                        }
                    }
                }
                if (trainReady == null) {
                    return null;
                }
                break;
            default:
                return candidate;
        }
        return candidate;
    }

    void registerHandler(SortingHandler handler) {
        this.handlers.add(handler);
        handler.init(this);
    }

    private static class HiddenException extends RuntimeException {
        public HiddenException(String message) {
            super(message);
        }
    }

    private static class HiddenWatchImpl implements SortingHandler {

        private final SortingHill sortingHill;

        HiddenWatchImpl(SortingHill sortingHill) {
            this.sortingHill = sortingHill;
        }

        @Override
        public void init(SortingHill sortingHill) {
            //пусто
        }

        @Override
        public String handleWagon(String wagonInfo) {
            if (sortingHill.assignedPaths.isEmpty()) {
                throw new HiddenException("no path");
            }
            String[] wagonDescr = wagonInfo.split("/");
            String wagonType = wagonDescr[wagonDescr.length - 1];
            String trainCandidate = null;
            Integer trainPath = null;
            for (Integer path : sortingHill.assignedPaths.keySet()) {
                String train = sortingHill.assignedPaths.get(path);
                if (train != null) {
                    String trainType = String.valueOf(train.charAt(train.length() - 1));
                    List<String> trainContent = sortingHill.trainsFormed.get(train);
                    if (trainContent != null && !trainContent.isEmpty()) {
                        String locoInfo = trainContent.getFirst();
                        String[] locoDescr = locoInfo.split("-");
                        int locoMaxWagons = Integer.parseInt(locoDescr[locoDescr.length - 1]);
                        if (locoMaxWagons > trainContent.size() - 1) {
                            switch (trainType) {
                                case WAGON_GRUZ_OPASN:
                                case WAGON_GRUZ:
                                    if (wagonType.equals(trainType) || wagonType.equals(WAGON_EMPTY)) {
                                        trainCandidate = train;
                                    }
                                    break;
                                case WAGON_PASS:
                                    if (wagonType.equals(trainType)) {
                                        trainCandidate = train;
                                    }
                                    break;
                                default:
                                    if (!wagonType.equals(WAGON_PASS) || trainContent.size() == 1) { //пассажирский вагон только в свой тип (выше обработано) или в пустой поезд (только локомотив)
                                        trainCandidate = train; //тип поезда ещё не назначен, любой тип вагона примет, но придётся это учесть далее
                                    }
                            }
                        }
                    }
                }
                if (trainCandidate != null) {
                    trainPath = path;
                    break;
                }
            }
            if (trainCandidate != null) {
                String trainType = null;
                for (String type : TRAIN_TYPES) {
                    if (trainCandidate.endsWith(type)) {
                        trainType = type;
                    }
                }
                if (trainType == null && !wagonType.equals(WAGON_EMPTY)) {
                    trainType = wagonType;
                    String typedTrainCandidate = trainCandidate + trainType;
                    sortingHill.trainsFormed.put(typedTrainCandidate, sortingHill.trainsFormed.remove(trainCandidate));
                    sortingHill.assignedPaths.put(trainPath, typedTrainCandidate);
                    trainCandidate = typedTrainCandidate;
                    System.out.println("тип поезда определён " + typedTrainCandidate);
                }
                sortingHill.trainsFormed.get(trainCandidate).add(wagonInfo);
                return trainCandidate;
            } else {
                throw new HiddenException("no free train");
            }
        }

        @Override
        public String handleLocomotive(String loco) {
            String trainCandidate = null;
            for (String train : sortingHill.trainsFormed.keySet()) {
                if (sortingHill.trainsFormed.get(train).isEmpty()) {
                    sortingHill.trainsFormed.get(train).add(loco);
                    trainCandidate = train;
                }
            }
            if (trainCandidate == null) {
                throw new HiddenException("no train to add loco");
            }
            return trainCandidate;
        }

        @Override
        public Integer preparePath() {
            Integer preparePath = null;
            for (int p = 1; p <= sortingHill.numberOfPaths; p++) {
                if (!sortingHill.assignedPaths.containsKey(p)) {
                    preparePath = p;
                    break;
                }
            }
            if (preparePath != null) {
                sortingHill.assignedPaths.put(preparePath, null);
                return preparePath;
            } else {
                throw new HiddenException("no free path");
            }
        }

        @Override
        public Map<Integer, String> allocatePathForTrain() {
            Integer allocatePath = null;
            for (Integer path : sortingHill.assignedPaths.keySet()) {
                if (sortingHill.assignedPaths.get(path) == null) {
                    allocatePath = path;
                }
            }
            if (allocatePath != null) {
                String nextTrain = String.format("%04d", ++sortingHill.trainIndex);
                sortingHill.assignedPaths.put(allocatePath, nextTrain);
                sortingHill.trainsFormed.put(nextTrain, new ArrayList<>());
                return Collections.singletonMap(allocatePath, nextTrain);
            } else {
                throw new HiddenException("no path for train");
            }
        }

        @Override
        public String sendTrain() {
            String trainReady = null;
            Integer trainPath = null;
            for (Integer path : sortingHill.assignedPaths.keySet()) {
                String train = sortingHill.assignedPaths.get(path);
                if (train == null) {
                    continue;
                }
                List<String> trainContent = sortingHill.trainsFormed.get(train);
                if (!trainContent.isEmpty()) {
                    String maybeLoco = trainContent.getFirst();
                    if (!LOCO_TYPES.contains(maybeLoco)) {
                        throw new HiddenException("no loco found for train " + train);
                    }
                    String[] locoDescr = maybeLoco.split("-");
                    int trainSize = Integer.parseInt(locoDescr[1]);
                    if (trainContent.size() == trainSize + 1 || (trainContent.size() > 1 && sortingHill.wagonBuffer.isEmpty())) {
                        trainReady = train;
                        trainPath = path;
                        break;
                    }
                }
            }
            if (trainReady == null) {
                throw new HiddenException("no ready train");
            }
            System.out.printf("поезд %s отправляется с пути %d\n", trainReady, trainPath);
            sortingHill.trainsFormed.remove(trainReady);
            sortingHill.assignedPaths.remove(trainPath);
            return trainReady;
        }

        @Override
        public void startShift() {
            //пусто
        }

        @Override
        public void endShift() {

        }
    }

}
