package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.Ecommerce;
import com.luan.FinancialSystem.repository.EcommerceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EcommerceService
{
    private final EcommerceRepository repository;

    public EcommerceService(EcommerceRepository repository){
        this.repository=repository;
    }

    public Ecommerce save(Ecommerce ecommerce){
        return repository.save(ecommerce);
    }

    public List<Ecommerce> list(){
        return repository.findAll();
    }
}
