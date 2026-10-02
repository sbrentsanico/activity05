package edu.cit.sanico.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface ProcessedTianggeOrderRepository extends JpaRepository<ProcessedTianggeOrder, String> {
}
