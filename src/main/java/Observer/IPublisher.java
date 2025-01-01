package Observer;

public interface IPublisher {
    void notify(NotificationType type, Object o);
    void addSubscriber(ISubscriber subscriber);
    void removeSubscriber(ISubscriber subscriber);
}
