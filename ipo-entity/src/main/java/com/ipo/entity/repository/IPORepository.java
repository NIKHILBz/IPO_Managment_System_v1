package com.ipo.entity.repository;

import com.ipo.entity.model.IPO;
import com.ipo.entity.model.IPOStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IPORepository extends JpaRepository<IPO, Long> {
    List<IPO> findByStatus(IPOStatus status);
    List<IPO> findByCompanyId(Long companyId);
}
