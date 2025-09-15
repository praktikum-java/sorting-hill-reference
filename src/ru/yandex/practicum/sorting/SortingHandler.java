package ru.yandex.practicum.sorting;

import ru.yandex.practicum.SortingHill;

import java.util.Map;

/*
    реализуйте интерфейс оператора
 */
public interface SortingHandler {

    /**
     * метод инициалиации внутреннего состояния оператора
     * @param sortingHill горка, над которой будет выполняться работа
     */
    default void init(SortingHill sortingHill) {
        throw new UnsupportedOperationException();
    }

    /**
     * обработчик поступающих вагонов
     * @param wagonInfo описание вагона в формате НОМЕР/Т(ип)
     * @return номер поезда, в который попал вагон
     */
    String handleWagon(String wagonInfo);

    /**
     * обработчик поступающих локомотивов
     * @param locomotive модель локомотива в формате МОДЕЛЬ-ЧислоВагоновМакс
     * @return номер поезда, в который попал локомотив
     */
    String handleLocomotive(String locomotive);

    /**
     * запрос на подготовку пути
     * @return подготовленный путь
     */
    Integer preparePath();

    /**
     * запрос на размещение поезда на пути
     * @return пара номер-пути:  номер поезда
     */
    Map<Integer, String> allocatePathForTrain();

    /**
     * запрос на отправку готового поезда
     * @return номер отправленного поезда
     */
    String sendTrain();

    /**
     * начало смены
     */
    void startShift();

    /**
     * окончание смены
     */
    void endShift();

}
