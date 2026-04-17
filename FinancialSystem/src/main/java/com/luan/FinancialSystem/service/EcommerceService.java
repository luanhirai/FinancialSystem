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

    public Ecommerce create(Ecommerce ecommerce){
        return repository.save(ecommerce);
    }

    public Ecommerce edit(Long id, Ecommerce updatedEcommerce){
        Ecommerce ecommerce = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ecommerce não encontrado"));

        ecommerce.setName(updatedEcommerce.getName());
        ecommerce.setRate(updatedEcommerce.getRate());
        ecommerce.setFixed_rate(updatedEcommerce.getFixed_rate());

        return repository.save(ecommerce);
    }

    public void deleteEcommerce(Long id){
        Ecommerce ecommerce= repository.findById(id).orElseThrow(()->new RuntimeException("Ecommerce não encontrado"));
        // fazer busca em produtos relacionados com este ecommerce
        repository.deleteById(id);
    }

    public List<Ecommerce> list(){
        return repository.findAll();
    }
}
