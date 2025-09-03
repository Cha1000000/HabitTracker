Рекомендованный план
1. Фильтрация исходящих приглашений в DAO
    - Изменить HabitInvitationDao.getOutgoingInvitations(userId) на запрос только status='PENDING'.
    - Это гарантирует мгновенное скрытие из исходящего списка после изменения статуса (accept/decline/cancel) и избавит от лишних элементов в UI.

2. Reconcile для исходящих приглашений
   - В SocialRepositoryImpl.getOutgoingHabitInvitations() добавить блок наподобие friend requests:
     - Собрать remoteIds из снепшота.
     - В фоне получить локальные getOutgoingInvitations(userId).first() и удалить PENDING, которых нет в remoteIds через
habitInvitationDao.deleteById(...).
   - Это покрывает случай, когда статус меняется на сервере (например, получатель принял), а локальная запись ещё не обновилась через outbox.

3. Reconcile для входящих
   - Схожий блок можно добавить и в getIncomingHabitInvitations() для симметрии, но входящие уже фильтруются 
по PENDING и локальный Room-first апдейт делает скрытие моментальным.

4. Ретест
- Проверить, что после accept/decline бейдж и список обновляются без рефреша (входящие и исходящие).
- Подтвердить отсутствие дублей логов.
- Оставить логи на уровне счетчиков.